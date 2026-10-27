/* Host matching shared with the original native blocker; kept independently testable. */
"use strict";
const VeyraRules = (() => {
  const baseline = ["doubleclick.net", "googlesyndication.com", "googleadservices.com",
    "google-analytics.com", "googletagservices.com", "adservice.google.com", "adnxs.com",
    "adsrvr.org", "criteo.com", "criteo.net", "outbrain.com", "taboola.com",
    "scorecardresearch.com", "quantserve.com", "hotjar.com", "hotjar.io", "ads-twitter.com",
    "amazon-adsystem.com", "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com", "demdex.net"];
  const canonical = host => host.toLowerCase().replace(/\.$/, "");
  function parse(text) {
    const hosts = new Set(baseline);
    for (const line of text.split(/\r?\n/)) {
      const parts = line.trim().split(/\s+/, 3);
      if (parts[0] === "0.0.0.0" && parts[1] && /^[a-z0-9.-]+$/i.test(parts[1])) hosts.add(canonical(parts[1]));
    }
    return hosts;
  }
  function blocks(hosts, requestHost, pageHost) {
    if (!requestHost || !pageHost) return false;
    const host = canonical(requestHost), page = canonical(pageHost);
    if (host === page || host.endsWith("." + page)) return false;
    for (let candidate = host; candidate.includes("."); candidate = candidate.slice(candidate.indexOf(".") + 1)) {
      if (hosts.has(candidate)) return page !== candidate && !page.endsWith("." + candidate);
    }
    return false;
  }
  return {parse, blocks};
})();
if (typeof module !== "undefined") module.exports = VeyraRules;
