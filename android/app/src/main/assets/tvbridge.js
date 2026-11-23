/* TVunboxed bridge adapter.
   The original demo2.html design is untouched except for calling these hooks. */
(function () {
  'use strict';

  var seq = 0;
  var pending = {};

  window.TvCallback = function (id, raw) {
    var p = pending[id];
    if (!p) return;
    delete pending[id];
    var result = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (result && result.ok) p.resolve(result.data || {});
    else {
      var e = new Error((result && result.error) || 'bridge failed');
      e.detail = result;
      p.reject(e);
    }
  };

  window.Tv = {
    call: function (op, args) {
      return new Promise(function (resolve, reject) {
        var id = 'tv' + (++seq);
        pending[id] = { resolve: resolve, reject: reject };
        try {
          AndroidTvBridge.invoke(id, op, JSON.stringify(args || {}));
        } catch (e) {
          delete pending[id];
          reject(e);
          return;
        }
        setTimeout(function () {
          if (pending[id]) {
            delete pending[id];
            reject(new Error('timeout: ' + op));
          }
        }, 15000);
      });
    }
  };

  /* ---- connection helpers used by demo2.html ---- */
  window.__tvScan = function () {
    return window.Tv.call('scan').then(function (r) {
      var list = (r.devices || []).map(function (d) {
        return {
          n: d.n, m: d.m, ip: d.ip, on: d.on,
          controlPort: d.controlPort, httpPort: d.httpPort
        };
      });
      if (window.__TV_SET_DEVICES__) window.__TV_SET_DEVICES__(list);
      return list;
    });
  };

  window.__tvConnect = function (d) {
    return window.Tv.call('connect', {
      ip: d.ip,
      name: d.n,
      controlPort: d.controlPort || 8121,
      httpPort: d.httpPort || 8000
    });
  };

  window.__tvStatus = function () {
    return window.Tv.call('getStatus');
  };

  /* native accelerometer -> DeviceOrientation-shaped data for the air mouse */
  window.__tvMotionData = function (gamma, beta) {
    if (window.__tvOnOri__) window.__tvOnOri__({ gamma: gamma, beta: beta });
  };

  /* TV screen on phone: captures frames from the TV and sends taps back */
  window.__tvShowScreen = function () {
    var host = document.getElementById('device') || document.body;
    var old = document.getElementById('tv-screen-share');
    if (old) { old.remove(); return; }
    var box = document.createElement('div');
    box.id = 'tv-screen-share';
    box.style.cssText = 'position:absolute;inset:0;z-index:95;background:#05060c;display:flex;flex-direction:column;gap:8px;padding:12px';
    var img = document.createElement('img');
    img.style.cssText = 'flex:1;width:100%;object-fit:contain;background:#000;border:2px solid #636889';
    var bar = document.createElement('div');
    bar.style.cssText = 'display:flex;gap:8px';
    var close = document.createElement('button');
    close.textContent = 'Close';
    close.style.cssText = 'flex:1;min-height:46px;background:#1f2238;color:#edeffc;border:2px solid #636889;font-weight:700';
    close.onclick = function () { stop = true; box.remove(); };
    bar.appendChild(close);
    box.appendChild(img);
    box.appendChild(bar);
    host.appendChild(box);
    var stop = false;
    img.addEventListener('click', function (e) {
      var r = img.getBoundingClientRect();
      window.Tv.call('touch', {
        x: (e.clientX - r.left) / r.width * 1000,
        y: (e.clientY - r.top) / r.height * 1000
      }).catch(function () {});
    });
    function loop() {
      if (stop) return;
      window.Tv.call('captureFrame').then(function (r) {
        if (!r.data) return setTimeout(loop, 1500);
        var mime = r.data.indexOf('iVBOR') === 0 ? 'image/png' : 'image/jpeg';
        img.src = 'data:' + mime + ';base64,' + r.data;
        setTimeout(loop, 500);
      }).catch(function () { setTimeout(loop, 1500); });
    }
    loop();
  };

  setTimeout(function () {
    var mirror = document.querySelector('[data-toast="Screen mirroring started"]');
    if (mirror) mirror.addEventListener('click', function () { window.__tvShowScreen(); });
  }, 0);
})();
