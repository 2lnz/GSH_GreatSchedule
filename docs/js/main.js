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

  var ticking = false;
  window.addEventListener('scroll', function () {
    if (!ticking) {
      ticking = true;
      requestAnimationFrame(function () {
        var scrollPos = window.scrollY + 120;
        var current = sections[0];
        sections.forEach(function (s) {
          if (s.el.offsetTop <= scrollPos) current = s;
        });
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

  // ---- ESC to close menu ----
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && hamburger.classList.contains('open')) {
      closeMenu();
    }
  });

})();
