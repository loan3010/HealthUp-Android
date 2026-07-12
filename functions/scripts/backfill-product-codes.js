#!/usr/bin/env node
/**
 * Assign HEALTHUP-xxxx codes to products missing productCode.
 *
 * Usage (from functions/):
 *   set GOOGLE_APPLICATION_CREDENTIALS=..\..\healthup-f6eff-firebase-adminsdk-fbsvc-ba1f26c21b.json
 *   node scripts/backfill-product-codes.js
 *   node scripts/backfill-product-codes.js --dry-run
 */

const admin = require('firebase-admin');

function parseArgs(argv) {
  return {dryRun: argv.includes('--dry-run'), help: argv.includes('--help') || argv.includes('-h')};
}

function formatCode(sequence) {
  return `HEALTHUP-${String(sequence).padStart(4, '0')}`;
}

function parseExistingCode(code) {
  if (!code || typeof code !== 'string') return 0;
  const match = code.trim().match(/^HEALTHUP-(\d+)$/i);
  return match ? parseInt(match[1], 10) : 0;
}

function initAdmin() {
  if (admin.apps.length === 0) {
    admin.initializeApp();
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    console.log('Usage: node scripts/backfill-product-codes.js [--dry-run]');
    return;
  }

  initAdmin();
  const db = admin.firestore();
  const snap = await db.collection('products').get();

  const missing = [];
  let maxSeq = 0;

  for (const doc of snap.docs) {
    const code = doc.get('productCode');
    const parsed = parseExistingCode(code);
    if (parsed > maxSeq) maxSeq = parsed;
    if (!code || !String(code).trim()) {
      missing.push(doc);
    }
  }

  const metaRef = db.collection('meta').doc('productCodes');
  const metaSnap = await metaRef.get();
  let counter = maxSeq + 1;
  if (metaSnap.exists) {
    const stored = metaSnap.get('next');
    if (typeof stored === 'number' && stored > counter) {
      counter = stored;
    }
  }

  console.log(`Products total: ${snap.size}`);
  console.log(`Missing productCode: ${missing.length}`);
  console.log(`Next sequence: ${counter}`);
  if (args.dryRun) {
    missing.slice(0, 5).forEach((doc, i) => {
      console.log(`  [dry-run] ${doc.id} -> ${formatCode(counter + i)} (${doc.get('name') || 'no name'})`);
    });
    if (missing.length > 5) console.log(`  ... and ${missing.length - 5} more`);
    return;
  }

  if (missing.length === 0) {
    console.log('Nothing to backfill.');
    return;
  }

  let assigned = 0;
  for (const doc of missing) {
    const code = formatCode(counter);
    await doc.ref.set({productCode: code}, {merge: true});
    console.log(`  ${doc.id} -> ${code} (${doc.get('name') || 'no name'})`);
    counter += 1;
    assigned += 1;
  }

  await metaRef.set({next: counter}, {merge: true});
  console.log(`\nDone. Assigned ${assigned} codes. Counter now at ${counter}.`);
}

main().catch((err) => {
  console.error('Error:', err.message || err);
  process.exitCode = 1;
});
