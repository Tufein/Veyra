/* Run with node --test tools/protection_test.cjs. */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {parse, blocks} = require('../app/src/main/assets/veyra-protection/rules.js');
const hosts = parse(readFileSync(new URL('../app/src/main/assets/adblock-hosts.txt', `file://${__filename}`), 'utf8'));

test('bundled snapshot and baseline block third-party domains and subdomains', () => {
  assert.ok(hosts.size > 56000);
  assert.equal(blocks(hosts, 'ads.doubleclick.net', 'example.com'), true);
  assert.equal(blocks(hosts, 'DOUBLECLICK.NET.', 'example.com'), true);
});
test('first-party navigation and subdomains remain available', () => {
  assert.equal(blocks(hosts, 'doubleclick.net', 'doubleclick.net'), false);
  assert.equal(blocks(hosts, 'ads.doubleclick.net', 'doubleclick.net'), false);
  assert.equal(blocks(hosts, 'example.com', 'example.com'), false);
});
test('host matching respects domain boundaries', () => {
  assert.equal(blocks(hosts, 'notdoubleclick.net', 'example.com'), false);
  assert.equal(blocks(hosts, 'doubleclick.net.evil.test', 'example.com'), false);
  assert.equal(blocks(hosts, '', 'example.com'), false);
});

const vm = require('node:vm');
async function background() {
  let onMessage, onRequest;
  const messages = [];
  const context = vm.createContext({URL, console, module: {exports: {}}, fetch: async () => ({ok: true, text: async () => '0.0.0.0 ads.test'}),
    browser: {
      runtime: {getURL: name => name, connectNative: () => ({onMessage: {addListener: fn => {onMessage = fn;}}, postMessage: msg => messages.push(msg)})},
      tabs: {onRemoved: {addListener: () => {}}},
      storage: {local: {set: () => {}}},
      webRequest: {onBeforeRequest: {addListener: fn => {onRequest = fn;}}}
    }});
  vm.runInContext(readFileSync(new URL('../app/src/main/assets/veyra-protection/rules.js', `file://${__filename}`), 'utf8'), context);
  await vm.runInContext(readFileSync(new URL('../app/src/main/assets/veyra-protection/background.js', `file://${__filename}`), 'utf8'), context);
  return {configure: onMessage, request: details => onRequest({tabId: 1, ...details}), messages};
}
test('request interceptor keeps first party pages and counts blocked resources', async () => {
  const bg = await background();
  bg.configure({type: 'config', blocking: true, httpsOnly: false, exceptions: []});
  assert.notEqual(bg.request({type: 'main_frame', url: 'https://example.com/'}).cancel, true);
  assert.equal(bg.request({type: 'image', url: 'https://ads.test/banner', documentUrl: 'https://example.com/'}).cancel, true);
  assert.equal(bg.messages.filter(m => m.type === 'blocked').length, 1);
});
test('site exceptions apply inside third party frames and global off takes effect', async () => {
  const bg = await background();
  bg.configure({type: 'config', blocking: true, httpsOnly: false, exceptions: ['example.com']});
  bg.request({type: 'main_frame', url: 'https://example.com/'});
  assert.notEqual(bg.request({type: 'script', url: 'https://ads.test/ad', documentUrl: 'https://third-party.test/iframe'}).cancel, true);
  bg.configure({type: 'config', blocking: false, httpsOnly: false, exceptions: []});
  assert.notEqual(bg.request({type: 'script', url: 'https://ads.test/ad', documentUrl: 'https://example.com/'}).cancel, true);
});
test('HTTPS-only blocks HTTP main pages and subresources even with ad blocking off', async () => {
  const bg = await background();
  bg.configure({type: 'config', blocking: false, httpsOnly: true, exceptions: ['example.com']});
  assert.equal(bg.request({type: 'main_frame', url: 'http://example.com/'}).cancel, true);
  assert.equal(bg.request({type: 'image', url: 'http://example.com/image', documentUrl: 'https://example.com/'}).cancel, true);
  assert.notEqual(bg.request({type: 'main_frame', url: 'https://example.com/'}).cancel, true);
});
