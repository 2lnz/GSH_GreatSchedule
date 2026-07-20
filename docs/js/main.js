// GSH Schedule — Mobile nav + scroll highlight + fade-in
(function () {
  'use strict';

  // ---- Hamburger menu ----
  var hamburger = document.getElementById('hamburger');
  var overlay = document.getElementById('mobileOverlay');
  var mobileLinks = overlay.querySelectorAll('a');
  var body = document.body;

  function openMenu() {
    hamburger.classList.add('open');
    overlay.classList.add('open');
    body.classList.add('menu-open');
  }
  function closeMenu() {
    hamburger.classList.remove('open');
    overlay.classList.remove('open');
    body.classList.remove('menu-open');
  }

  hamburger.addEventListener('click', function () {
    if (hamburger.classList.contains('open')) { closeMenu(); }
    else { openMenu(); }
  });

  overlay.addEventListener('click', function (e) {
    if (e.target === overlay) { closeMenu(); }
  });

  mobileLinks.forEach(function (link) {
    link.addEventListener('click', function () { closeMenu(); });
  });

  // ---- Nav highlight on scroll ----
  var desktopNavLinks = document.querySelectorAll('.nav-links a');
  var sections = [];
  var sectionTops = []; // cached offsetTop values to avoid forced reflow

  function cacheSectionPositions() {
    sectionTops = [];
    sections.forEach(function (s) {
      sectionTops.push(s.el.offsetTop);
    });
  }

  desktopNavLinks.forEach(function (link) {
    var href = link.getAttribute('href');
    if (href && href.startsWith('#')) {
      var el = document.getElementById(href.substring(1));
      if (el) sections.push({ id: href.substring(1), el: el, link: link });
    }
  });

  sections.sort(function (a, b) {
    return a.el.offsetTop - b.el.offsetTop;
  });
  cacheSectionPositions();
  window.addEventListener('resize', cacheSectionPositions);

  var ticking = false;
  window.addEventListener('scroll', function () {
    if (!ticking) {
      ticking = true;
      requestAnimationFrame(function () {
        var scrollPos = window.scrollY + 120;
        var current = sections[0];
        for (var i = 0; i < sections.length; i++) {
          if (sectionTops[i] <= scrollPos) current = sections[i];
        }
        desktopNavLinks.forEach(function (l) { l.classList.remove('active'); });
        if (current) current.link.classList.add('active');
        ticking = false;
      });
    }
  }, { passive: true });

  // ---- Scroll-triggered fade-in ----
  var fadeEls = document.querySelectorAll('.fade-in');
  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (entry.isIntersecting) {
        entry.target.style.animationPlayState = 'running';
      }
    });
  }, { threshold: 0.1 });

  fadeEls.forEach(function (el) {
    el.style.animationPlayState = 'paused';
    observer.observe(el);
  });

  // ---- FAQ accordion ----
  document.querySelectorAll('.faq-q').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var expanded = btn.getAttribute('aria-expanded') === 'true';
      // Close all others
      document.querySelectorAll('.faq-q[aria-expanded="true"]').forEach(function (b) {
        if (b !== btn) {
          b.setAttribute('aria-expanded', 'false');
          b.nextElementSibling.classList.remove('open');
        }
      });
      // Toggle current
      btn.setAttribute('aria-expanded', expanded ? 'false' : 'true');
      btn.nextElementSibling.classList.toggle('open', !expanded);
    });
  });

  // ---- Disable right-click & DevTools shortcuts ----
  document.addEventListener('contextmenu', function (e) { e.preventDefault(); });

  document.addEventListener('keydown', function (e) {
    // F12
    if (e.key === 'F12') { e.preventDefault(); return; }
    // Ctrl+Shift+I / Ctrl+Shift+J / Ctrl+Shift+C / Ctrl+U
    if (e.ctrlKey && e.shiftKey && (e.key === 'I' || e.key === 'J' || e.key === 'C')) { e.preventDefault(); return; }
    if (e.ctrlKey && e.key === 'U') { e.preventDefault(); return; }
  });

  // ---- ESC to close menu ----
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && hamburger.classList.contains('open')) {
      closeMenu();
    }
  });

})();
