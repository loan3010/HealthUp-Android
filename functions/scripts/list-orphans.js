#!/usr/bin/env node
/**
 * List all Auth/Firestore orphan users (no deletion).
 *
 * Usage (from functions/):
 *   set GOOGLE_APPLICATION_CREDENTIALS=..\healthup-f6eff-firebase-adminsdk-fbsvc-ba1f26c21b.json
 *   node scripts/list-orphans.js
 *   node scripts/list-orphans.js --limit 20
 */

const admin = require('firebase-admin');
const {normalizePhone} = require('../lib/orphanCleanup');

const SYNTHETIC_DOMAIN = '@healthup.app';

function parseArgs(argv) {
  const args = {limit: 10, json: false};
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--limit') {
      args.limit = parseInt(argv[++i], 10) || 10;
    } else if (arg === '--json') {
      args.json = true;
    } else if (arg === '--help' || arg === '-h') {
      args.help = true;
    }
  }
  return args;
}

function initAdmin() {
  if (admin.apps.length === 0) {
    admin.initializeApp();
  }
}

function describeFirestoreDoc(doc) {
  const data = doc.data() || {};
  return {
    uid: doc.id,
    fullName: data.fullName || null,
    phone: data.phone || null,
    displayEmail: data.displayEmail || null,
    email: data.email || null,
    role: data.role || null,
  };
}

function describeAuthUser(user) {
  return {
    uid: user.uid,
    email: user.email || null,
    phoneNumber: user.phoneNumber || null,
    providers: (user.providerData || []).map((p) => p.providerId),
    createdAt: user.metadata?.creationTime || null,
  };
}

async function listAllAuthUsers(auth) {
  const users = [];
  let pageToken;
  do {
    const result = await auth.listUsers(1000, pageToken);
    users.push(...result.users);
    pageToken = result.pageToken;
  } while (pageToken);
  return users;
}

async function listAllFirestoreUsers(db) {
  const docs = [];
  let lastDoc = null;
  const pageSize = 500;
  while (true) {
    let query = db.collection('users').orderBy(admin.firestore.FieldPath.documentId()).limit(pageSize);
    if (lastDoc) {
      query = query.startAfter(lastDoc);
    }
    const snap = await query.get();
    if (snap.empty) break;
    docs.push(...snap.docs);
    lastDoc = snap.docs[snap.docs.length - 1];
    if (snap.size < pageSize) break;
  }
  return docs;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    console.log(`Usage: node scripts/list-orphans.js [--limit N] [--json]`);
    return;
  }

  initAdmin();
  const db = admin.firestore();
  const auth = admin.auth();

  const [authUsers, firestoreDocs] = await Promise.all([
    listAllAuthUsers(auth),
    listAllFirestoreUsers(db),
  ]);

  const firestoreByUid = new Map(firestoreDocs.map((d) => [d.id, d]));
  const authByUid = new Map(authUsers.map((u) => [u.uid, u]));

  const authOnly = [];
  const firestoreOnly = [];
  const linked = [];

  for (const user of authUsers) {
    const profile = firestoreByUid.get(user.uid);
    if (profile) {
      linked.push({auth: describeAuthUser(user), firestore: describeFirestoreDoc(profile)});
    } else {
      authOnly.push(describeAuthUser(user));
    }
  }

  for (const doc of firestoreDocs) {
    if (!authByUid.has(doc.id)) {
      firestoreOnly.push(describeFirestoreDoc(doc));
    }
  }

  const report = {
    counts: {
      firestoreUsers: firestoreDocs.length,
      authUsers: authUsers.length,
      linked: linked.length,
      authOnly: authOnly.length,
      firestoreOnly: firestoreOnly.length,
    },
    authOnlyExamples: authOnly.slice(0, args.limit),
    firestoreOnlyExamples: firestoreOnly.slice(0, args.limit),
    syntheticAuthOnly: authOnly.filter((u) => u.email && u.email.endsWith(SYNTHETIC_DOMAIN)).length,
    realEmailAuthOnly: authOnly.filter((u) => u.email && !u.email.endsWith(SYNTHETIC_DOMAIN)).length,
  };

  if (args.json) {
    console.log(JSON.stringify(report, null, 2));
    return;
  }

  console.log('=== HealthUp orphan scan (read-only) ===\n');
  console.log('Counts:');
  console.log(`  Firestore users:  ${report.counts.firestoreUsers}`);
  console.log(`  Auth users:       ${report.counts.authUsers}`);
  console.log(`  Linked (both):    ${report.counts.linked}`);
  console.log(`  Auth-only:        ${report.counts.authOnly} (synthetic: ${report.syntheticAuthOnly}, real email: ${report.realEmailAuthOnly})`);
  console.log(`  Firestore-only:   ${report.counts.firestoreOnly}`);

  if (report.authOnlyExamples.length > 0) {
    console.log(`\n--- Auth-only examples (first ${report.authOnlyExamples.length}) ---`);
    for (const u of report.authOnlyExamples) {
      console.log(`  ${u.uid}  ${u.email || '(no email)'}  providers=${u.providers.join(',')}`);
    }
  }

  if (report.firestoreOnlyExamples.length > 0) {
    console.log(`\n--- Firestore-only examples (first ${report.firestoreOnlyExamples.length}) ---`);
    for (const p of report.firestoreOnlyExamples) {
      console.log(`  ${p.uid}  phone=${p.phone || '-'}  displayEmail=${p.displayEmail || '-'}  email=${p.email || '-'}`);
    }
  }

  if (report.counts.authOnly > 0 && report.counts.firestoreOnly > 0) {
    console.log('\nNote: Auth-only (synthetic email) blocks registration at OTP; Firestore-only blocks forgot-password.');
  }
}

main().catch((err) => {
  console.error('Error:', err.message || err);
  process.exitCode = 1;
});
