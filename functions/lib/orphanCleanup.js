/**
 * Shared logic for orphan user cleanup (Firestore profile vs Firebase Auth).
 * Used by scripts/cleanup-orphans.js — run locally with firebase-admin.
 */

const SYNTHETIC_EMAIL_DOMAIN = '@healthup.app';

function normalizeEmail(email) {
  if (!email || typeof email !== 'string') return null;
  const normalized = email.trim().toLowerCase();
  return normalized.length > 0 ? normalized : null;
}

function isRealEmail(email) {
  return !!email && !email.endsWith(SYNTHETIC_EMAIL_DOMAIN);
}

function normalizePhone(input) {
  if (!input || typeof input !== 'string') return '';
  let digits = input.replace(/[^0-9]/g, '');
  if (!digits) return '';
  if (digits.startsWith('84')) digits = digits.slice(2);
  if (digits.length === 9) return '0' + digits;
  if (digits.length === 10 && digits.startsWith('0')) return digits;
  if (digits.length > 10 && digits.startsWith('0')) return digits.slice(0, 10);
  return digits;
}

function phoneLookupVariants(input) {
  const normalized = normalizePhone(input);
  if (!normalized) return [];
  const variants = new Set([normalized]);
  if (normalized.length === 10 && normalized.startsWith('0')) {
    const withoutZero = normalized.slice(1);
    variants.add(withoutZero);
    variants.add('+84' + withoutZero);
    variants.add('84' + withoutZero);
  }
  return [...variants];
}

function syntheticAuthEmail(phone) {
  const normalized = normalizePhone(phone);
  return normalized ? normalized + SYNTHETIC_EMAIL_DOMAIN : null;
}

async function authUserExists(auth, uid) {
  try {
    await auth.getUser(uid);
    return true;
  } catch (err) {
    if (err.code === 'auth/user-not-found') return false;
    throw err;
  }
}

async function authUserByEmail(auth, email) {
  try {
    return await auth.getUserByEmail(email);
  } catch (err) {
    if (err.code === 'auth/user-not-found') return null;
    throw err;
  }
}

async function findFirestoreProfiles(db, {email, phone, uid}) {
  const profiles = new Map();

  const addDoc = (doc, matchedField) => {
    if (!profiles.has(doc.id)) {
      profiles.set(doc.id, {
        uid: doc.id,
        matchedField,
        data: doc.data(),
      });
    }
  };

  if (uid) {
    const doc = await db.collection('users').doc(uid).get();
    if (doc.exists) addDoc(doc, 'uid');
  }

  const normalizedEmail = normalizeEmail(email);
  if (normalizedEmail) {
    for (const field of ['displayEmail', 'email']) {
      const snap = await db.collection('users').where(field, '==', normalizedEmail).get();
      snap.docs.forEach((doc) => addDoc(doc, field));
    }
  }

  for (const variant of phoneLookupVariants(phone || '')) {
    const snap = await db.collection('users').where('phone', '==', variant).get();
    snap.docs.forEach((doc) => addDoc(doc, 'phone'));
  }

  return [...profiles.values()];
}

async function buildAuthTargets(auth, {email, phone, firestoreProfiles}) {
  const targets = new Map();
  const add = (record, reason) => {
    if (!record || !record.uid) return;
    targets.set(record.uid, {
      uid: record.uid,
      email: record.email || null,
      reason,
    });
  };

  for (const profile of firestoreProfiles) {
    const exists = await authUserExists(auth, profile.uid);
    if (exists) {
      add({uid: profile.uid, email: profile.data.email}, 'firestore_profile_uid');
    }
  }

  const normalizedEmail = normalizeEmail(email);
  if (normalizedEmail && isRealEmail(normalizedEmail)) {
    const byEmail = await authUserByEmail(auth, normalizedEmail);
    if (byEmail) add(byEmail, 'auth_email_lookup');
  }

  const synthetic = syntheticAuthEmail(phone || '');
  if (synthetic) {
    const bySynthetic = await authUserByEmail(auth, synthetic);
    if (bySynthetic) add(bySynthetic, 'auth_synthetic_phone_email');
  }

  return [...targets.values()];
}

function describeProfile(profile) {
  const data = profile.data || {};
  return {
    uid: profile.uid,
    matchedField: profile.matchedField,
    fullName: data.fullName || null,
    phone: data.phone || null,
    displayEmail: data.displayEmail || null,
    email: data.email || null,
    role: data.role || null,
  };
}

/**
 * @param {import('firebase-admin')} admin
 * @param {{ email?: string, phone?: string, uid?: string }} query
 */
async function previewOrphans(admin, query) {
  const db = admin.firestore();
  const auth = admin.auth();

  const email = query.email || '';
  const phone = query.phone || '';
  const uid = query.uid || '';

  if (!email && !phone && !uid) {
    throw new Error('Provide at least one of: --email, --phone, or --uid');
  }

  const firestoreProfiles = await findFirestoreProfiles(db, {email, phone, uid});
  const authTargets = await buildAuthTargets(auth, {email, phone, firestoreProfiles});

  const profileUids = new Set(firestoreProfiles.map((p) => p.uid));
  const authUids = new Set(authTargets.map((t) => t.uid));

  const firestoreOnly = firestoreProfiles.filter((p) => !authUids.has(p.uid));
  const authOnly = authTargets.filter((t) => !profileUids.has(t.uid));
  const linked = firestoreProfiles.filter((p) => authUids.has(p.uid));

  const phonesToClean = new Set();
  for (const p of firestoreProfiles) {
    if (p.data && p.data.phone) phonesToClean.add(normalizePhone(p.data.phone));
  }
  const normalizedInputPhone = normalizePhone(phone);
  if (normalizedInputPhone) phonesToClean.add(normalizedInputPhone);

  const emailVerificationDocs = [];
  for (const p of firestoreProfiles) {
    const doc = await db.collection('email_verification').doc(p.uid).get();
    if (doc.exists) {
      emailVerificationDocs.push({uid: p.uid, data: doc.data()});
    }
  }

  const otpDocs = [];
  for (const p of phonesToClean) {
    if (!p) continue;
    for (const collection of ['registration_otp', 'password_reset']) {
      const doc = await db.collection(collection).doc(p).get();
      if (doc.exists) {
        otpDocs.push({collection, phone: p, data: doc.data()});
      }
    }
  }

  return {
    query: {email: email || null, phone: phone || null, uid: uid || null},
    summary: {
      firestoreProfiles: firestoreProfiles.length,
      authUsers: authTargets.length,
      firestoreOnly: firestoreOnly.length,
      authOnly: authOnly.length,
      linked: linked.length,
      emailVerificationDocs: emailVerificationDocs.length,
      otpDocs: otpDocs.length,
    },
    firestoreProfiles: firestoreProfiles.map(describeProfile),
    authUsers: authTargets,
    firestoreOnly: firestoreOnly.map(describeProfile),
    authOnly,
    linked: linked.map(describeProfile),
    emailVerificationDocs,
    otpDocs,
    hint: firestoreOnly.length > 0
      ? 'Firestore-only profiles block "Email đã được sử dụng" / "SĐT đã tồn tại" during registration.'
      : null,
  };
}

async function deleteFirestoreProfile(db, uid) {
  const ref = db.collection('users').doc(uid);
  if (typeof db.recursiveDelete === 'function') {
    await db.recursiveDelete(ref);
  } else {
    await ref.delete();
  }
}

async function deleteOtpDoc(db, collection, phone) {
  await db.collection(collection).doc(phone).delete();
}

/**
 * @param {import('firebase-admin')} admin
 * @param {{ email?: string, phone?: string, uid?: string, dryRun?: boolean }} options
 */
async function cleanupOrphans(admin, options) {
  const dryRun = options.dryRun !== false;
  const report = await previewOrphans(admin, options);

  if (dryRun) {
    return {...report, dryRun: true, deleted: null};
  }

  const db = admin.firestore();
  const auth = admin.auth();
  const deleted = {
    firestoreProfiles: [],
    authUsers: [],
    emailVerification: [],
    otpDocs: [],
    errors: [],
  };

  const uidsToDeleteFirestore = new Set([
    ...report.firestoreOnly.map((p) => p.uid),
    ...report.linked.map((p) => p.uid),
  ]);

  for (const uid of uidsToDeleteFirestore) {
    try {
      await deleteFirestoreProfile(db, uid);
      deleted.firestoreProfiles.push(uid);
    } catch (err) {
      deleted.errors.push({type: 'firestore_profile', uid, message: err.message});
    }
  }

  for (const doc of report.emailVerificationDocs) {
    try {
      await db.collection('email_verification').doc(doc.uid).delete();
      deleted.emailVerification.push(doc.uid);
    } catch (err) {
      deleted.errors.push({type: 'email_verification', uid: doc.uid, message: err.message});
    }
  }

  for (const doc of report.otpDocs) {
    try {
      await deleteOtpDoc(db, doc.collection, doc.phone);
      deleted.otpDocs.push(`${doc.collection}/${doc.phone}`);
    } catch (err) {
      deleted.errors.push({
        type: 'otp',
        id: `${doc.collection}/${doc.phone}`,
        message: err.message,
      });
    }
  }

  const authUidsToDelete = new Set(report.authUsers.map((u) => u.uid));
  for (const uid of authUidsToDelete) {
    try {
      await auth.deleteUser(uid);
      deleted.authUsers.push(uid);
    } catch (err) {
      if (err.code !== 'auth/user-not-found') {
        deleted.errors.push({type: 'auth', uid, message: err.message});
      }
    }
  }

  return {...report, dryRun: false, deleted};
}

module.exports = {
  normalizeEmail,
  normalizePhone,
  phoneLookupVariants,
  syntheticAuthEmail,
  previewOrphans,
  cleanupOrphans,
};
