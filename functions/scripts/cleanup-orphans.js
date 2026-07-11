#!/usr/bin/env node
/**
 * Dọn user orphan trên Firebase (Firestore + Auth).
 *
 * Dùng khi xóa tay trên Console chỉ xóa Authentication mà quên Firestore
 * → đăng ký lại vẫn báo "Email đã được sử dụng".
 *
 * Cách chạy (từ thư mục functions/):
 *
 *   # Xem trước — không xóa gì
 *   node scripts/cleanup-orphans.js --email user@gmail.com
 *   node scripts/cleanup-orphans.js --phone 0912345678
 *   node scripts/cleanup-orphans.js --uid abc123
 *
 *   # Thực sự xóa (cần xác nhận)
 *   node scripts/cleanup-orphans.js --email user@gmail.com --execute
 *   node scripts/cleanup-orphans.js --phone 0912345678 --execute --yes
 *
 * Xác thực (chọn một):
 *   - Đặt GOOGLE_APPLICATION_CREDENTIALS trỏ tới service account JSON
 *   - Hoặc: gcloud auth application-default login
 *   - Hoặc: firebase login + export GOOGLE_APPLICATION_CREDENTIALS (service account từ Firebase Console)
 */

const path = require('path');
const readline = require('readline');
const admin = require('firebase-admin');
const {previewOrphans, cleanupOrphans} = require('../lib/orphanCleanup');

function parseArgs(argv) {
  const args = {
    email: '',
    phone: '',
    uid: '',
    execute: false,
    yes: false,
    help: false,
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case '--email':
        args.email = argv[++i] || '';
        break;
      case '--phone':
        args.phone = argv[++i] || '';
        break;
      case '--uid':
        args.uid = argv[++i] || '';
        break;
      case '--execute':
      case '--delete':
        args.execute = true;
        break;
      case '--yes':
      case '-y':
        args.yes = true;
        break;
      case '--help':
      case '-h':
        args.help = true;
        break;
      default:
        break;
    }
  }
  return args;
}

function printHelp() {
  console.log(`
HealthUp — cleanup orphan Firebase users

Usage:
  node scripts/cleanup-orphans.js [--email E] [--phone P] [--uid U] [--execute] [--yes]

Options:
  --email E     Email thật (displayEmail) cần kiểm tra / dọn
  --phone P     Số điện thoại (0xxxxxxxxx hoặc +84...)
  --uid U       UID Firestore / Auth
  --execute     Thực hiện xóa (mặc định chỉ xem trước)
  --yes         Bỏ qua hỏi xác nhận khi --execute
  --help        Hiện trợ giúp

Ví dụ:
  node scripts/cleanup-orphans.js --email test@gmail.com
  node scripts/cleanup-orphans.js --phone 0912345678 --execute --yes
`);
}

function askConfirm(question) {
  const rl = readline.createInterface({input: process.stdin, output: process.stdout});
  return new Promise((resolve) => {
    rl.question(question, (answer) => {
      rl.close();
      resolve(/^y(es)?$/i.test((answer || '').trim()));
    });
  });
}

function initAdmin() {
  if (admin.apps.length === 0) {
    admin.initializeApp();
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    printHelp();
    return;
  }

  if (!args.email && !args.phone && !args.uid) {
    printHelp();
    process.exitCode = 1;
    return;
  }

  initAdmin();

  try {
    if (!args.execute) {
      const report = await previewOrphans(admin, args);
      console.log(JSON.stringify(report, null, 2));
      console.log('\nChế độ xem trước. Để xóa, thêm --execute');
      return;
    }

    const preview = await previewOrphans(admin, args);
    console.log('=== XEM TRƯỚC ===');
    console.log(JSON.stringify(preview.summary, null, 2));
    if (preview.hint) console.log('\nGợi ý:', preview.hint);

    const willDelete =
        preview.summary.firestoreProfiles > 0 ||
        preview.summary.authUsers > 0 ||
        preview.summary.emailVerificationDocs > 0 ||
        preview.summary.otpDocs > 0;

    if (!willDelete) {
      console.log('\nKhông có gì để xóa.');
      return;
    }

    if (!args.yes) {
      const ok = await askConfirm(
          '\nBạn chắc chắn muốn XÓA dữ liệu trên? (yes/no): '
      );
      if (!ok) {
        console.log('Đã hủy.');
        return;
      }
    }

    const result = await cleanupOrphans(admin, {...args, dryRun: false});
    console.log('\n=== ĐÃ XÓA ===');
    console.log(JSON.stringify(result.deleted, null, 2));
    if (result.deleted.errors && result.deleted.errors.length > 0) {
      console.log('\nLỗi một phần:', result.deleted.errors);
      process.exitCode = 2;
    }
  } catch (err) {
    console.error('Lỗi:', err.message || err);
    process.exitCode = 1;
  }
}

main();
