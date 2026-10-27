"use strict";
(async () => {
  const response = await fetch(browser.runtime.getURL("hosts.txt"));
  if (!response.ok) throw new Error("Bundled protection rules unavailable");
  const hosts = VeyraRules.parse(await response.text());
  const pages = new Map();
  browser.tabs.onRemoved.addListener(id => pages.delete(id));
  let config = {blocking: true, httpsOnly: false, exceptions: [], privateExceptions: []};
  const cosmetic = privateMode => ({blocking: config.blocking,
    exceptions: privateMode ? (config.privateExceptions || config.exceptions) : config.exceptions});
  // Keep all configuration in memory. Private hosts must never enter extension storage.
  browser.storage.local.remove("config").catch(() => {});
  browser.runtime.onMessage.addListener((message, sender) => {
    if (message && message.type === "cosmetic-config" && sender.tab) return Promise.resolve(cosmetic(sender.tab.incognito));
    return undefined;
  });
  const port = browser.runtime.connectNative("veyraProtection");
  port.onMessage.addListener(async message => {
    if (message.type === "site-cookie-counts") {
      try {
        const cookies = await browser.cookies.getAll({storeId: "firefox-default"});
        const counts = Object.create(null); let domains = 0;
        for (const cookie of cookies) {
          const host = cookie.domain.replace(/^\./, "").toLowerCase();
          if (!Object.hasOwn(counts, host)) { if (domains >= 2000) continue; counts[host] = 0; domains++; }
          counts[host]++;
        }
        port.postMessage({type: "site-cookie-counts", requestId: message.requestId, counts});
      } catch (_) {
        port.postMessage({type: "site-cookie-counts", requestId: message.requestId, error: true});
      }
      return;
    }
    if (message.type !== "config") return;
    config = message;
    browser.tabs.query({}).then(tabs => {
      for (const tab of tabs) browser.tabs.sendMessage(tab.id, {type: "protection-config", config: cosmetic(tab.incognito)}).catch(() => {});
    }).catch(() => {});
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
    const exceptions = details.incognito ? (config.privateExceptions || config.exceptions) : config.exceptions;
    if (exceptions.includes(pageHost)) return {};
    if (VeyraRules.blocks(hosts, url.hostname, pageHost)) {
      port.postMessage({type: "blocked"});
      return {cancel: true};
    }
    return {};
  }, {urls: ["http://*/*", "https://*/*"]}, ["blocking"]);
  port.postMessage({type: "ready"});
})();
