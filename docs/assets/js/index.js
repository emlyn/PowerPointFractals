let currentTab;
let currentSlide = 0;
let focusMode = false;
let slides;
let thumbs;
let foci;

function init() {
  slides = document.getElementsByClassName("slide");
  foci = document.getElementsByClassName("focus");
  thumbs = document.querySelectorAll(".thumbnails img");
  handleLocationChanged();
  showSlide();
}

function gettab(arg) {
  if (typeof arg == 'string') {
    return [arg, document.getElementById(arg + '-button'), document.getElementById(arg + '-tab')];
  } else if (typeof arg == 'object') {
    return [arg.id.replace(/-button$/, ''), arg, document.getElementById(arg.id.replace(/-button$/, '-tab'))];
  }
}

function tab(arg) {
  const [name, button, tab] = gettab(arg);
  // console.log('Tab', name, button, tab);

  for (const el of document.querySelectorAll("div.tab")) {
    el.classList.remove("active");
  }
  tab.classList.add("active");

  for (const el of document.querySelectorAll("a.tab")) {
    el.classList.remove("active");
  }
  button.classList.add("active");
  currentTab = name;
}

function slide(n, force=false) {
  if (force && currentTab != 'gallery') {
    tab('gallery');
  }
  while (n < 0) {
    n += slides.length;
  }
  n = n % slides.length;
  currentSlide = n;
  showSlide();
}

function nextSlide() {
  slide(currentSlide + 1);
}

function prevSlide() {
  slide(currentSlide - 1);
}

function showSlide() {
  for (const slide of slides) {
    slide.classList.remove("active");
  }
  for (const thumb of thumbs) {
    thumb.classList.remove("active");
  }
  for (const focus of foci) {
    focus.classList.remove("active");
  }
  if (focusMode) {
    foci[currentSlide].classList.add("active");
  }
  slides[currentSlide].classList.add("active");
  thumbs[currentSlide].classList.add("active");
  thumbs[currentSlide].scrollIntoView({ behavior: "smooth", inline: "center" });
}

function focusImage(active) {
  focusMode = active;
  for (const focus of foci) {
    focus.classList.remove("active");
  }
  if (focusMode) {
    foci[currentSlide].classList.add("active");
  }
}

function handleKey(e) {
  // console.log(e);
  switch (e.key) {
    case "ArrowLeft":
      if (currentTab == 'gallery') {
        prevSlide();
      }
      break;
    case "ArrowRight":
      if (currentTab == 'gallery') {
        nextSlide();
      }
      break;
    case "Escape":
      tab(currentTab == 'index' ? 'gallery' : 'index');
      break;
    case " ":
    case "Enter":
      if (currentTab == 'gallery') {
        focusImage(!focusMode);
      }
      break;
  }
}

function handleResize() {
  if (thumbs) {
    thumbs[currentSlide].scrollIntoView({ inline: "center" });
  }
}

function handleLocationChanged() {
  const hash = location.hash.substring(1);
  // console.log('Location', hash);
  tab(hash || 'home');
}

window.onhashchange = handleLocationChanged;
addEventListener("keydown", handleKey, { passive: true });
addEventListener("resize", handleResize, { passive: true });
addEventListener("DOMContentLoaded", init, { passive: true });
