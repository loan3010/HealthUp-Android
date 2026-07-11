const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

/**
 * Callable: reset Auth password after Firestore password_reset OTP was verified.
 * Prefer update by Auth UID (Firestore users/{uid} id) — reliable even when
 * Firestore.email is still synthetic after verifyBeforeUpdateEmail.
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
