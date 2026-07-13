/**
 * Optional server-side utilities (admin scripts / Blaze deploy).
 *
 * HealthUp Android is Spark-only and does NOT call any HTTPS callable here:
 * - Registration / forgot-password / social flows use Firestore + client Auth only.
 * - Orphan cleanup: AuthOrphanCleaner.java (sign-in + delete), not Cloud Functions.
 * - Password reset: PasswordResetRepository writes passwordHash on users/{uid}; Auth secret unchanged.
 *
 * exports.resetPassword below is legacy / unused by the Android app — kept for manual ops only.
 */
const functions = require('firebase-functions');
const admin = require('firebase-admin');
const crypto = require('crypto');
const {
  previewOrphans,
  cleanupOrphans,
} = require('./lib/orphanCleanup');

const APP_PASSWORD_PEPPER = 'HealthUp-Spark-AppPassword-v1';

admin.initializeApp();

/** Must match AppPasswordHelper.authSecretForAdminEmail in Android. */
function authSecretForAdminEmail(email) {
  const normalized = String(email || '').trim().toLowerCase();
  const digest = crypto.createHash('sha256')
      .update(`${APP_PASSWORD_PEPPER}\nadmin-auth\n${normalized}`)
      .digest('hex');
  return `Ha1!${digest.substring(0, 28)}`;
}

async function assertAdminCallable(context) {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'Sign in required');
  }
  const doc = await admin.firestore().collection('users').doc(context.auth.uid).get();
  const data = doc.data() || {};
  const role = (data.role || data.userRole || '').toString().toLowerCase();
  if (role !== 'admin') {
    throw new functions.https.HttpsError('permission-denied', 'Admin only');
  }
}

/**
 * Legacy callable — NOT used by HealthUp Android (Spark mock reset uses Firestore passwordHash).
 * Deploy only if you need server-side Auth password updates outside the app.
 */
exports.resetPassword = functions.https.onCall(async (data) => {
  const phone = data.phone;
  const newPassword = data.newPassword;
  const uidHint = data.uid;
  const emailHint = data.email;

  if (!phone || !newPassword) {
    throw new functions.https.HttpsError('invalid-argument', 'Missing phone or password');
  }

  if (newPassword.length < 8) {
    throw new functions.https.HttpsError('invalid-argument', 'Password too short');
  }

  const resetDoc = await admin.firestore().collection('password_reset').doc(phone).get();
  if (!resetDoc.exists || !resetDoc.data().verified) {
    throw new functions.https.HttpsError('failed-precondition', 'OTP not verified');
  }

  const users = await admin.firestore()
      .collection('users')
      .where('phone', '==', phone)
      .limit(1)
      .get();
  if (users.empty) {
    throw new functions.https.HttpsError('not-found', 'User not found');
  }

  const userDoc = users.docs[0];
  const uid = uidHint || userDoc.id;

  try {
    await admin.auth().updateUser(uid, {password: newPassword});
  } catch (err) {
    // Fallback: resolve Auth user by email (displayEmail / email on profile).
    const profile = userDoc.data() || {};
    const candidates = [emailHint, profile.displayEmail, profile.email]
        .filter((e) => typeof e === 'string' && e.length > 0);

    let updated = false;
    let lastError = err;
    for (const email of candidates) {
      try {
        const userRecord = await admin.auth().getUserByEmail(email);
        await admin.auth().updateUser(userRecord.uid, {password: newPassword});
        updated = true;
        break;
      } catch (e2) {
        lastError = e2;
      }
    }
    if (!updated) {
      console.error('resetPassword updateUser failed', lastError);
      throw new functions.https.HttpsError(
          'not-found',
          'Auth user not found for this phone/email'
      );
    }
  }

  await admin.firestore().collection('password_reset').doc(phone).delete();
  return {success: true};
});

/**
 * After admin forgot-password OTP verified: set Firebase Auth password to the app
 * derived secret so login works immediately (same model as buyer phone auth secret).
 */
exports.syncAdminAuthAfterReset = functions.https.onCall(async (data) => {
  const adminUid = data && data.adminUid;
  if (!adminUid || typeof adminUid !== 'string') {
    throw new functions.https.HttpsError('invalid-argument', 'Missing adminUid');
  }

  const resetDoc = await admin.firestore()
      .collection('admin_password_reset')
      .doc(adminUid)
      .get();
  if (!resetDoc.exists || !resetDoc.data().verified) {
    throw new functions.https.HttpsError('failed-precondition', 'OTP not verified');
  }

  const userDoc = await admin.firestore().collection('users').doc(adminUid).get();
  if (!userDoc.exists) {
    throw new functions.https.HttpsError('not-found', 'Admin profile not found');
  }
  const profile = userDoc.data() || {};
  const role = (profile.role || profile.userRole || '').toString().toLowerCase();
  if (role !== 'admin') {
    throw new functions.https.HttpsError('permission-denied', 'Not an admin account');
  }

  const email = (resetDoc.data().email || profile.email || profile.displayEmail || '')
      .toString()
      .trim()
      .toLowerCase();
  if (!email) {
    throw new functions.https.HttpsError('failed-precondition', 'Admin email missing');
  }

  const derivedPassword = authSecretForAdminEmail(email);
  try {
    await admin.auth().updateUser(adminUid, {password: derivedPassword});
  } catch (err) {
    try {
      const userRecord = await admin.auth().getUserByEmail(email);
      await admin.auth().updateUser(userRecord.uid, {password: derivedPassword});
    } catch (err2) {
      console.error('syncAdminAuthAfterReset failed', err, err2);
      throw new functions.https.HttpsError(
          'internal',
          'Could not sync Firebase Auth password'
      );
    }
  }

  await admin.firestore().collection('admin_password_reset').doc(adminUid).delete();
  return {success: true};
});

/**
 * Callable (admin): preview Firestore / Auth orphans blocking re-registration.
 */
exports.previewOrphanCleanup = functions.https.onCall(async (data, context) => {
  await assertAdminCallable(context);
  try {
    return await previewOrphans(data || {});
  } catch (err) {
    throw new functions.https.HttpsError('invalid-argument', err.message || 'Invalid request');
  }
});

/**
 * Callable (admin): delete orphan Firestore profiles + Auth users (+ OTP aux docs).
 */
exports.cleanupOrphanUser = functions.https.onCall(async (data, context) => {
  await assertAdminCallable(context);
  try {
    return await cleanupOrphans({...(data || {}), execute: true});
  } catch (err) {
    console.error('cleanupOrphanUser failed', err);
    throw new functions.https.HttpsError('internal', err.message || 'Cleanup failed');
  }
});
