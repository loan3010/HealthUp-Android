#!/usr/bin/env node
/**
 * Broad search for an email across Firestore + Auth (one-off diagnostic).
 * Usage: node scripts/search-email-broad.js xuanmai200506@gmail.com
 */

const admin = require('firebase-admin');
const path = require('path');

const TARGET = (process.argv[2] || 'xuanmai200506@gmail.com').trim();
const SYNTHETIC_DOMAIN = '@healthup.app';

function normalizeEmail(email) {
  if (!email || typeof email !== 'string') return null;
  const n = email.trim().toLowerCase();
  return n.length ? n : null;
}

function caseVariants(email) {
  const base = email.trim();
  const variants = new Set([
    base,
    base.toLowerCase(),
    base.toUpperCase(),
    normalizeEmail(base),
  ]);
  // common typo / partial
  if (base.includes('@')) {
    const [local, domain] = base.split('@');
    variants.add(`${local}@${domain.toLowerCase()}`);
    variants.add(`${local}@${domain.toUpperCase()}`);
  }
  return [...variants].filter(Boolean);
}

function emailMatches(value, needle) {
  if (!value || typeof value !== 'string') return false;
  const v = value.toLowerCase();
  const n = needle.toLowerCase();
  return v === n || v.includes('xuanmai') || v.includes(n.split('@')[0]);
}

function initAdmin() {
  if (admin.apps.length === 0) {
    const saPath = process.env.GOOGLE_APPLICATION_CREDENTIALS
      || path.resolve(__dirname, '../../healthup-f6eff-firebase-adminsdk-fbsvc-ba1f26c21b.json');
    admin.initializeApp({
      credential: admin.credential.cert(require(saPath)),
      projectId: 'healthup-f6eff',
    });
  }
}

async function queryUsersField(db, field, value) {
  const snap = await db.collection('users').where(field, '==', value).get();
  return snap.docs.map((d) => ({id: d.id, field, value, data: d.data()}));
}

async function scanUsersCollection(db, needle) {
  const hits = [];
  let last = null;
  const batchSize = 500;
  while (true) {
    let q = db.collection('users').orderBy(admin.firestore.FieldPath.documentId()).limit(batchSize);
    if (last) q = q.startAfter(last);
    const snap = await q.get();
    if (snap.empty) break;
    for (const doc of snap.docs) {
      const data = doc.data();
      const fields = ['displayEmail', 'email', 'fullName'];
      for (const f of fields) {
        if (emailMatches(data[f], needle)) {
          hits.push({
            id: doc.id,
            matchedField: f,
            matchedValue: data[f],
            phone: data.phone || null,
            fullName: data.fullName || null,
          });
        }
      }
    }
    last = snap.docs[snap.docs.length - 1];
    if (snap.size < batchSize) break;
  }
  return hits;
}

async function scanCollectionForEmail(db, collectionName, needle) {
  const hits = [];
  const snap = await db.collection(collectionName).get();
  for (const doc of snap.docs) {
    const data = doc.data();
    const json = JSON.stringify(data).toLowerCase();
    if (json.includes(needle.toLowerCase()) || json.includes('xuanmai')) {
      hits.push({collection: collectionName, docId: doc.id, data});
    }
  }
  return hits;
}

async function listAuthByEmail(auth, email) {
  try {
    const user = await auth.getUserByEmail(email);
    return {found: true, uid: user.uid, email: user.email, providers: user.providerData.map((p) => p.providerId)};
  } catch (err) {
    if (err.code === 'auth/user-not-found') return {found: false};
    throw err;
  }
}

async function scanAuthUsers(auth, needle) {
  const hits = [];
  let nextPageToken;
  const normalized = normalizeEmail(needle);
  do {
    const result = await auth.listUsers(1000, nextPageToken);
    for (const user of result.users) {
      const em = (user.email || '').toLowerCase();
      if (em === normalized || em.includes('xuanmai') || em.includes(normalized.split('@')[0])) {
        hits.push({
          uid: user.uid,
          email: user.email,
          providers: user.providerData.map((p) => p.providerId),
          phoneNumber: user.phoneNumber || null,
        });
      }
    }
    nextPageToken = result.pageToken;
  } while (nextPageToken);
  return hits;
}

async function main() {
  initAdmin();
  const db = admin.firestore();
  const auth = admin.auth();
  const normalized = normalizeEmail(TARGET);
  const variants = caseVariants(TARGET);

  console.log('=== HealthUp email broad search ===');
  console.log('Project:', admin.app().options.projectId);
  console.log('Target:', TARGET);
  console.log('Normalized:', normalized);
  console.log('');

  const report = {
    projectId: admin.app().options.projectId,
    target: TARGET,
    normalized,
    exactFirestoreQueries: {},
    authByEmail: {},
    authFullScan: [],
    firestoreFullScan: [],
    otpCollections: {},
    syntheticCollisionCheck: null,
    cleanupScriptPreview: null,
  };

  // Exact Firestore queries per variant
  for (const v of variants) {
    for (const field of ['displayEmail', 'email']) {
      const key = `${field}==${v}`;
      const rows = await queryUsersField(db, field, v);
      if (rows.length) report.exactFirestoreQueries[key] = rows;
    }
  }

  // Auth lookup per variant
  for (const v of variants) {
    report.authByEmail[v] = await listAuthByEmail(auth, v);
  }
  report.authByEmail[normalized] = await listAuthByEmail(auth, normalized);

  // Full Auth scan (paginated)
  console.log('Scanning all Auth users (paginated)...');
  report.authFullScan = await scanAuthUsers(auth, TARGET);

  // Full Firestore users scan
  console.log('Scanning Firestore users collection...');
  report.firestoreFullScan = await scanUsersCollection(db, TARGET);

  // OTP / verification collections
  for (const coll of ['registration_otp', 'password_reset', 'email_verification']) {
    report.otpCollections[coll] = await scanCollectionForEmail(db, coll, TARGET);
  }

  // Synthetic pattern: could user's phone collide if they enter phone that maps to existing auth?
  // Extract local part before @ for gmail — not applicable for synthetic.
  // Check if any auth user has email === normalized (real email in auth)
  const realInAuth = report.authFullScan.filter((u) => normalizeEmail(u.email) === normalized);
  report.realEmailInAuth = realInAuth;

  // Run orphan cleanup preview for comparison
  const {previewOrphans} = require('../lib/orphanCleanup');
  report.cleanupScriptPreview = await previewOrphans(admin, {email: normalized});

  console.log(JSON.stringify(report, null, 2));

  const blockers = [];
  if (Object.keys(report.exactFirestoreQueries).length) blockers.push('exact Firestore query match');
  if (report.firestoreFullScan.length) blockers.push('Firestore full-scan partial match');
  if (report.authByEmail[normalized]?.found) blockers.push('Auth getUserByEmail');
  if (report.realEmailInAuth.length) blockers.push('Auth user with real email');
  if (report.otpCollections.email_verification?.length) blockers.push('email_verification doc');

  console.log('\n=== SUMMARY ===');
  console.log('Blockers found:', blockers.length ? blockers.join(', ') : 'NONE in Firebase data');
  console.log('Orphan cleanup summary:', report.cleanupScriptPreview.summary);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
