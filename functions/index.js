const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

exports.resetPassword = functions.https.onCall(async (data) => {
  const phone = data.phone;
  const newPassword = data.newPassword;

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

  const email = users.docs[0].data().email;
  if (!email) {
    throw new functions.https.HttpsError('not-found', 'User email not found');
  }

  const userRecord = await admin.auth().getUserByEmail(email);
  await admin.auth().updateUser(userRecord.uid, {password: newPassword});
  await admin.firestore().collection('password_reset').doc(phone).delete();

  return {success: true};
});
