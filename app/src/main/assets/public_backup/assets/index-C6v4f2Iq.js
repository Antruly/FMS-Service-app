(function(){const t=document.createElement("link").relList;if(t&&t.supports&&t.supports("modulepreload"))return;for(const n of document.querySelectorAll('link[rel="modulepreload"]'))s(n);new MutationObserver(n=>{for(const i of n)if(i.type==="childList")for(const c of i.addedNodes)c.tagName==="LINK"&&c.rel==="modulepreload"&&s(c)}).observe(document,{childList:!0,subtree:!0});function o(n){const i={};return n.integrity&&(i.integrity=n.integrity),n.referrerPolicy&&(i.referrerPolicy=n.referrerPolicy),n.crossOrigin==="use-credentials"?i.credentials="include":n.crossOrigin==="anonymous"?i.credentials="omit":i.credentials="same-origin",i}function s(n){if(n.ep)return;n.ep=!0;const i=o(n);fetch(n.href,i)}})();const r=typeof window.AndroidApp<"u",l=document.querySelector(".loading-screen"),w=document.querySelector(".error-screen"),f=document.querySelector(".loading-dots");let d=0;const u=setInterval(()=>{d=(d+1)%4,f.textContent=".".repeat(d+1)},500);function p(){clearInterval(u),l.classList.add("hidden"),setTimeout(()=>{l.style.display="none"},500)}function g(){clearInterval(u),l.style.display="none",w.classList.add("show")}const a={set(e,t){if(r)window.AndroidApp.setString(e,JSON.stringify(t));else try{localStorage.setItem(e,JSON.stringify(t))}catch{}},get(e,t=null){if(r){const o=window.AndroidApp.getString(e,null);if(o)try{return JSON.parse(o)}catch{return o}}else try{const o=localStorage.getItem(e);if(o)return JSON.parse(o)}catch{}return t}};window.__scanCallback=null;window.__qrLoginCallback=null;window.__handleScanResult=function(e){if(console.log("[Web] Received scan result:",e),e&&e.includes("/api/auth/qr-login/scan?token=")){var t=e.match(/token=([A-Za-z0-9]+)/);if(t&&t[1]){var o=t[1];console.log("[Web] QR Login token:",o),h(o);return}}window.__scanCallback&&window.__scanCallback(e)};function h(e){window.location.href="/api/auth/qr-login/confirm?token="+e}window.__openScanner=function(e){window.__scanCallback=e,r?window.AndroidApp.openScanner():e({error:"Web端不支持扫码，请在App中扫码"})};window.__showToast=function(e){r?window.AndroidApp.showToast(e):alert(e)};window.__saveLoginState=function(e,t){a.set("lastUsername",e.email||e.username||""),a.set("loginState",{user:e,token:t,time:Date.now()})};window.__getLoginState=function(){return a.get("loginState")};window.__getLastUsername=function(){return a.get("lastUsername","")};window.addEventListener("load",()=>{m(),setTimeout(()=>{p(),window.location.href="https://download.ssvr.top:8843/login.html"},1e3)});window.addEventListener("error",e=>{console.error("App error:",e)});function m(){const e=document.createElement("script");e.textContent=`
        (function() {
          // 等待页面加载完成
          window.addEventListener('load', function() {
            setTimeout(function() {
              // 保存原始的XMLHttpRequest和fetch
              const originalXHR = window.XMLHttpRequest;
              const originalFetch = window.fetch;

              // 注入登录状态处理
              if (typeof window.__saveLoginState === 'function') {
                // 拦截登录请求
                window.fetch = function(url, options) {
                  if (url.includes('/api/auth/login') && options && options.method === 'POST') {
                    return originalFetch.apply(this, arguments).then(function(response) {
                      response.clone().json().then(function(data) {
                        if (data.code === 0 && data.data) {
                          window.__saveLoginState(data.data.user, data.data.token);
                        }
                      }).catch(function() {});
                      return response;
                    });
                  }
                  return originalFetch.apply(this, arguments);
                };
              }

              // 检测是否原生App
              window.__isNativeApp = typeof window.AndroidApp !== 'undefined';
              console.log('[Web] Is native app:', window.__isNativeApp);
            }, 100);
          });
        })();
      `,document.head.appendChild(e)}window.showError=g;window.hideLoading=p;
