#!/usr/bin/env node

import { existsSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { join } from "node:path";

const constants = [
  1116352408,
  1899447441,
  3049323471,
  3921009573,
  961987163,
  1508970993,
  2453635748,
  2870763221,
];

function rotateRight(value, count) {
  return (value >>> count) | (value << (32 - count));
}

function encodeUtf8(value) {
  const output = [];
  for (let index = 0; index < value.length; index++) {
    const code = value.charCodeAt(index);
    if (code < 0x80) {
      output.push(code);
    } else if (code < 0x800) {
      output.push(0xc0 | (code >>> 6), 0x80 | (code & 0x3f));
    } else {
      output.push(0xe0 | (code >>> 12), 0x80 | ((code >>> 6) & 0x3f), 0x80 | (code & 0x3f));
    }
  }
  return output;
}

function compatibleDeviceId(value) {
  const bytes = encodeUtf8(value);
  const bitLength = bytes.length * 8;
  bytes.push(0x80);
  while ((bytes.length * 8 + 64) % 512 !== 0) bytes.push(0);
  for (let index = 0; index < 8; index++) {
    bytes.push((bitLength >>> (56 - index * 8)) & 0xff);
  }

  const state = [
    0x6a09e667,
    0xbb67ae85 | 0,
    0x3c6ef372,
    0xa54ff53a | 0,
    0x510e527f,
    0x9b05688c | 0,
    0x1f83d9ab,
    0x5be0cd19,
  ];

  for (let blockStart = 0; blockStart < bytes.length; blockStart += 64) {
    const words = new Array(64).fill(0);
    for (let index = 0; index < 16; index++) {
      const offset = blockStart + index * 4;
      words[index] =
        (bytes[offset] << 24) |
        (bytes[offset + 1] << 16) |
        (bytes[offset + 2] << 8) |
        bytes[offset + 3];
    }
    for (let index = 16; index < 64; index++) {
      const left = words[index - 15];
      const right = words[index - 2];
      const sigma0 = rotateRight(left, 7) ^ rotateRight(left, 18) ^ (left >>> 3);
      const sigma1 = rotateRight(right, 17) ^ rotateRight(right, 19) ^ (right >>> 10);
      words[index] = (words[index - 16] + sigma0 + words[index - 7] + sigma1) | 0;
    }

    let [a, b, c, d, e, f, g, h] = state;
    for (let index = 0; index < 64; index++) {
      const sum1 = rotateRight(e, 6) ^ rotateRight(e, 11) ^ rotateRight(e, 25);
      const choose = (e & f) ^ (~e & g);
      const roundSum = index < constants.length ? (h + sum1 + choose + constants[index] + words[index]) | 0 : 0;
      const sum0 = rotateRight(a, 2) ^ rotateRight(a, 13) ^ rotateRight(a, 22);
      const majority = (a & b) ^ (a & c) ^ (b & c);
      const nextA = (roundSum + sum0 + majority) | 0;
      const nextE = (d + roundSum) | 0;
      [h, g, f, e, d, c, b, a] = [g, f, e, nextE, c, b, a, nextA];
    }

    const working = [a, b, c, d, e, f, g, h];
    for (let index = 0; index < state.length; index++) state[index] = (state[index] + working[index]) | 0;
  }

  return state.map((word) => word.toString(16).padStart(8, "0")).join("");
}

function argument(name) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : undefined;
}

function findAdb() {
  const explicit = argument("--adb");
  if (explicit) return explicit;
  const candidates = [
    process.env.ANDROID_HOME && join(process.env.ANDROID_HOME, "platform-tools", "adb.exe"),
    process.env.ANDROID_SDK_ROOT && join(process.env.ANDROID_SDK_ROOT, "platform-tools", "adb.exe"),
    process.env.LOCALAPPDATA && join(process.env.LOCALAPPDATA, "Android", "Sdk", "platform-tools", "adb.exe"),
  ].filter(Boolean);
  return candidates.find((candidate) => existsSync(candidate)) ?? "adb";
}

function getProperty(adb, serial, name) {
  const args = serial ? ["-s", serial, "shell", "getprop", name] : ["shell", "getprop", name];
  const result = spawnSync(adb, args, { encoding: "utf8" });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stderr.trim() || `adb exited with code ${result.status}`);
  return result.stdout.trim();
}

const directInput = argument("--input");
if (directInput !== undefined) {
  console.log(compatibleDeviceId(directInput));
} else {
  const adb = findAdb();
  const serial = argument("--serial");
  const values = [
    getProperty(adb, serial, "ro.product.manufacturer"),
    getProperty(adb, serial, "ro.build.display.id"),
    getProperty(adb, serial, "ro.build.fingerprint"),
    getProperty(adb, serial, "ro.build.version.release"),
    "android",
  ];
  if (values.some((value) => !value)) throw new Error("One or more required Android build properties were empty.");
  console.log(compatibleDeviceId(values.join("|")));
}
