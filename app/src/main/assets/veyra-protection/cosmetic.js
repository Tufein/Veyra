"use strict";
(() => {
  const id = "veyra-adblock-style";
  function apply(config) {
    const old = document.getElementById(id);
    if (!config || !config.blocking || config.exceptions.includes(location.hostname.toLowerCase())) {
      if (old) old.remove(); return;
    }
    if (old || !document.documentElement) return;
    const style = document.createElement("style"); style.id = id;
    style.textContent = ".adsbygoogle,.ad-slot,.advertisement,.advertentie,[data-ad-slot],iframe[id^=google_ads_iframe]{display:none!important}";
    document.documentElement.appendChild(style);
  }
  browser.runtime.sendMessage({type: "cosmetic-config"}).then(apply).catch(() => {});
  browser.runtime.onMessage.addListener(message => { if (message.type === "protection-config") apply(message.config); });
})();
