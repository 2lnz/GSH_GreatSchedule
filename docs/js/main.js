/* ==========================================================================
   GSH课程表官网 — interactions
   Header state · anchors · mobile menu · hero slider · reveal · FAQ · to-top
   ========================================================================== */
(function () {
  'use strict';

  var doc = document;
  var body = doc.body;
  var prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ---------------------------------------------------------------
     Page loader (BOE-style logo + bar)
  --------------------------------------------------------------- */
  var pageLoader = doc.getElementById('pageLoader');
  var loaderHidden = false;

  function hidePageLoader() {
    if (!pageLoader || loaderHidden) return;
    loaderHidden = true;

    var elapsed = (window.performance && window.performance.now)
      ? window.performance.now()
      : 0;
    var wait = prefersReduced ? 0 : Math.max(0, 420 - elapsed);

    window.setTimeout(function () {
      pageLoader.classList.add('is-done');
      body.classList.remove('is-loading');
      window.setTimeout(function () {
        if (pageLoader.parentNode) pageLoader.parentNode.removeChild(pageLoader);
      }, 500);
    }, wait);
  }

  if (doc.readyState === 'complete') hidePageLoader();
  else window.addEventListener('load', hidePageLoader, { once: true });
  // Failsafe: never let a stalled asset trap the page behind the loader
  window.setTimeout(hidePageLoader, 5000);

  /* ---------------------------------------------------------------
     Header: solidify on scroll
  --------------------------------------------------------------- */
  var header = doc.getElementById('siteHeader');
  var lastScroll = -1;

  function syncHeader() {
    var y = window.scrollY || window.pageYOffset;
    if (y === lastScroll) return;
    lastScroll = y;
    if (header) header.classList.toggle('is-solid', y > 24);
  }

  var headerTick = false;
  window.addEventListener('scroll', function () {
    if (headerTick) return;
    headerTick = true;
    window.requestAnimationFrame(function () {
      syncHeader();
      headerTick = false;
    });
  }, { passive: true });
  syncHeader();

  /* ---------------------------------------------------------------
     Header-aware in-page anchors
  --------------------------------------------------------------- */
  function anchorOffset() {
    return (header ? header.offsetHeight : 74) + 24;
  }

  function scrollToAnchor(target, smooth) {
    var y = (window.scrollY || window.pageYOffset) +
            target.getBoundingClientRect().top - anchorOffset();
    window.scrollTo({
      top: Math.max(0, Math.round(y)),
      behavior: (smooth && !prefersReduced) ? 'smooth' : 'auto'
    });
  }

  doc.addEventListener('click', function (e) {
    if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey ||
        e.shiftKey || e.altKey) return;

    var link = e.target.closest ? e.target.closest('a[href]') : null;
    if (!link || link.classList.contains('skip-link')) return;
    if (link.target && link.target !== '_self') return;

    var url;
    try { url = new URL(link.href, window.location.href); } catch (err) { return; }
    if (url.origin !== window.location.origin) return;
    if (url.pathname !== window.location.pathname) return;
    if (!url.hash || url.hash === '#') return;

    var target = doc.getElementById(url.hash.slice(1));
    if (!target) return;

    e.preventDefault();
    if (window.history && window.history.pushState) {
      window.history.pushState(null, '', url.hash);
    }
    scrollToAnchor(target, true);
  });

  // Re-align when a page is opened directly with a hash
  window.addEventListener('load', function () {
    if (!window.location.hash || window.location.hash === '#') return;
    var target = doc.getElementById(window.location.hash.slice(1));
    if (target) window.setTimeout(function () { scrollToAnchor(target, false); }, 60);
  });

  /* ---------------------------------------------------------------
     Mobile menu
  --------------------------------------------------------------- */
  var menuToggle = doc.getElementById('menuToggle');
  var mobileMenu = doc.getElementById('mobileMenu');

  function closeMobileMenu() {
    if (!mobileMenu || !menuToggle) return;
    mobileMenu.classList.remove('open');
    menuToggle.classList.remove('open');
    menuToggle.setAttribute('aria-expanded', 'false');
    menuToggle.setAttribute('aria-label', '打开菜单');
    body.classList.remove('menu-open');
  }

  function toggleMobileMenu() {
    if (!mobileMenu || !menuToggle) return;
    var isOpen = mobileMenu.classList.toggle('open');
    menuToggle.classList.toggle('open', isOpen);
    menuToggle.setAttribute('aria-expanded', String(isOpen));
    menuToggle.setAttribute('aria-label', isOpen ? '关闭菜单' : '打开菜单');
    body.classList.toggle('menu-open', isOpen);
  }

  if (menuToggle && mobileMenu) {
    menuToggle.addEventListener('click', toggleMobileMenu);
    mobileMenu.querySelectorAll('a').forEach(function (link) {
      link.addEventListener('click', closeMobileMenu);
    });
    doc.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && mobileMenu.classList.contains('open')) {
        closeMobileMenu();
        menuToggle.focus();
      }
    });
    window.addEventListener('resize', function () {
      if (window.innerWidth > 1140) closeMobileMenu();
    });
  }

  /* ---------------------------------------------------------------
     Hero slider
  --------------------------------------------------------------- */
  var hero = doc.querySelector('.hero');
  var slides = Array.prototype.slice.call(doc.querySelectorAll('.hero-slide'));
  var dots = Array.prototype.slice.call(doc.querySelectorAll('.hero-dot'));
  var prevBtn = doc.querySelector('.hero-arrow--prev');
  var nextBtn = doc.querySelector('.hero-arrow--next');
  var pagerCurrent = doc.querySelector('.hero-pager b');
  var current = 0;
  var timer = null;
  var INTERVAL = 7500;

  function pad(n) { return n < 10 ? '0' + n : String(n); }

  function showSlide(index, userInitiated) {
    if (!slides.length) return;
    current = (index + slides.length) % slides.length;

    slides.forEach(function (slide, i) {
      slide.classList.toggle('is-active', i === current);
      slide.setAttribute('aria-hidden', i === current ? 'false' : 'true');
    });

    dots.forEach(function (dot, i) {
      var active = i === current;
      dot.classList.remove('is-active');
      dot.setAttribute('aria-current', String(active));
      if (active) {
        // Restart the progress animation by forcing reflow
        void dot.offsetWidth;
        dot.classList.add('is-active');
      }
    });

    if (pagerCurrent) pagerCurrent.textContent = pad(current + 1);
    if (userInitiated) restartTimer();
  }

  function nextSlide() { showSlide(current + 1, true); }
  function prevSlide() { showSlide(current - 1, true); }

  function startTimer() {
    if (prefersReduced || slides.length < 2) return;
    stopTimer();
    timer = window.setInterval(function () { showSlide(current + 1, false); }, INTERVAL);
  }
  function stopTimer() {
    if (timer) { window.clearInterval(timer); timer = null; }
  }
  function restartTimer() {
    stopTimer();
    startTimer();
  }

  if (slides.length) {
    if (nextBtn) nextBtn.addEventListener('click', nextSlide);
    if (prevBtn) prevBtn.addEventListener('click', prevSlide);
    dots.forEach(function (dot) {
      dot.addEventListener('click', function () {
        showSlide(parseInt(dot.getAttribute('data-slide'), 10) || 0, true);
      });
    });

    if (hero) {
      hero.addEventListener('mouseenter', stopTimer);
      hero.addEventListener('mouseleave', startTimer);
      hero.addEventListener('focusin', stopTimer);
      hero.addEventListener('focusout', function (e) {
        if (!hero.contains(e.relatedTarget)) startTimer();
      });
    }
    doc.addEventListener('visibilitychange', function () {
      if (doc.hidden) stopTimer();
      else startTimer();
    });

    showSlide(0, false);
    startTimer();
  }

  /* ---------------------------------------------------------------
     FAQ accordion
  --------------------------------------------------------------- */
  var faqButtons = Array.prototype.slice.call(doc.querySelectorAll('.faq-q'));

  function closeFaq(btn) {
    var panel = btn.nextElementSibling;
    btn.setAttribute('aria-expanded', 'false');
    if (panel) panel.style.maxHeight = '0px';
  }

  function openFaq(btn) {
    var panel = btn.nextElementSibling;
    btn.setAttribute('aria-expanded', 'true');
    if (panel) panel.style.maxHeight = panel.scrollHeight + 'px';
  }

  faqButtons.forEach(function (btn) {
    btn.addEventListener('click', function () {
      var expanded = btn.getAttribute('aria-expanded') === 'true';
      faqButtons.forEach(function (other) {
        if (other !== btn && other.getAttribute('aria-expanded') === 'true') {
          closeFaq(other);
        }
      });
      if (expanded) closeFaq(btn);
      else openFaq(btn);
    });
  });

  window.addEventListener('resize', function () {
    faqButtons.forEach(function (btn) {
      if (btn.getAttribute('aria-expanded') === 'true') {
        var panel = btn.nextElementSibling;
        if (panel) panel.style.maxHeight = panel.scrollHeight + 'px';
      }
    });
  });

  /* ---------------------------------------------------------------
     Scroll reveal
  --------------------------------------------------------------- */
  var revealEls = Array.prototype.slice.call(doc.querySelectorAll('[data-reveal]'));

  if ('IntersectionObserver' in window && !prefersReduced) {
    var revealObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-revealed');
          revealObserver.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });

    revealEls.forEach(function (el) { revealObserver.observe(el); });
  } else {
    revealEls.forEach(function (el) { el.classList.add('is-revealed'); });
  }

  /* ---------------------------------------------------------------
     Back to top
  --------------------------------------------------------------- */
  var toTop = doc.querySelector('.to-top');
  var topTick = false;

  function syncToTop() {
    var show = (window.scrollY || window.pageYOffset) > 600;
    if (toTop) toTop.classList.toggle('show', show);
  }

  if (toTop) {
    toTop.addEventListener('click', function () {
      window.scrollTo({ top: 0, behavior: prefersReduced ? 'auto' : 'smooth' });
    });
    window.addEventListener('scroll', function () {
      if (topTick) return;
      topTick = true;
      window.requestAnimationFrame(function () {
        syncToTop();
        topTick = false;
      });
    }, { passive: true });
    syncToTop();
  }

  /* ---------------------------------------------------------------
     Scroll spy for in-page navigation (home page)
  --------------------------------------------------------------- */
  var spyLinks = Array.prototype.slice.call(
    doc.querySelectorAll('.main-nav .nav-link[href^="#"]')
  );
  var homeLink = doc.querySelector('.main-nav .nav-link[href="index.html"]');

  if (spyLinks.length) {
    var spySections = spyLinks.map(function (link) {
      return {
        link: link,
        el: doc.getElementById(link.getAttribute('href').slice(1))
      };
    }).filter(function (item) { return item.el; });

    var spyTick = false;

    // Measure live positions so an expanded FAQ (or any layout change)
    // can never leave the highlight one section behind.
    function syncSpy() {
      // rect.top is viewport-relative, so compare with a viewport threshold
      var threshold = anchorOffset() + 40;
      var activeLink = null;
      for (var i = 0; i < spySections.length; i++) {
        if (spySections[i].el.getBoundingClientRect().top <= threshold) {
          activeLink = spySections[i].link;
        }
      }
      spyLinks.forEach(function (link) { link.classList.remove('is-active'); });
      if (activeLink) {
        if (homeLink) homeLink.classList.remove('is-active');
        activeLink.classList.add('is-active');
      } else if (homeLink) {
        homeLink.classList.add('is-active');
      }
    }

    window.addEventListener('resize', syncSpy);
    window.addEventListener('scroll', function () {
      if (spyTick) return;
      spyTick = true;
      window.requestAnimationFrame(function () {
        syncSpy();
        spyTick = false;
      });
    }, { passive: true });
    syncSpy();
  }
})();
