"use strict";
(async () => {
  const response = await fetch(browser.runtime.getURL("hosts.txt"));
  if (!response.ok) throw new Error("Bundled protection rules unavailable");
  const hosts = VeyraRules.parse(await response.text());
  const pages = new Map();
  browser.tabs.onRemoved.addListener(id => pages.delete(id));
  let config = {blocking: true, httpsOnly: false, exceptions: []};
  const port = browser.runtime.connectNative("veyraProtection");
  port.onMessage.addListener(message => {
    if (message.type !== "config") return;
    config = message;
    browser.storage.local.set({config});
    port.postMessage({type: "configured"});
  });
  browser.webRequest.onBeforeRequest.addListener(details => {
    const url = new URL(details.url);
    if (details.type === "main_frame") pages.set(details.tabId, url.hostname.toLowerCase());
    if (config.httpsOnly && url.protocol === "http:") return {cancel: true};
    if (!config.blocking || details.type === "main_frame") return {};
    let pageHost;
    try { pageHost = pages.get(details.tabId) || new URL(details.documentUrl || details.originUrl).hostname.toLowerCase(); }
    catch (_) { return {}; }
    if (config.exceptions.includes(pageHost)) return {};
    if (VeyraRules.blocks(hosts, url.hostname, pageHost)) {
      port.postMessage({type: "blocked"});
      return {cancel: true};
    }
    return {};
  }, {urls: ["http://*/*", "https://*/*"]}, ["blocking"]);
  port.postMessage({type: "ready"});
})();
