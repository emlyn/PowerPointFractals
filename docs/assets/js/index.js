let mode = 'intro';
let slideIndex = 0;
let slides;
let thumbs;
let foci;

const nextSlide = () => setSlide(slideIndex + 1);
const prevSlide = () => setSlide(slideIndex - 1);
const gotoSlide = n => setSlide(n);

function setSlide(n) {
    while (n < 0) {
        n += slides.length;
    }
    n = n % slides.length;
    slideIndex = n;
    showSlide();
}

function init() {
    slides = document.getElementsByClassName("slide");
    thumbs = document.querySelectorAll(".thumbnails img");
    foci = document.getElementsByClassName("focus");
    showSlide();
}

function setMode(m) {
    switch (m) {
        case 'intro':
            document.getElementById('intro').style.display = 'block';
            break;
        case 'slides':
            document.getElementById('intro').style.display = 'none';
            foci[slideIndex].style.display = "none";
            break;
        case 'focus':
            foci[slideIndex].style.display = "block";
            break;
        default:
            console.error(`Unknown mode: ${m}`);
            return;
    }
    mode = m;
}

function showSlide() {
    for (slide of slides) {
        slide.style.display = "none";
    }
    for (thumb of thumbs) {
        thumb.classList.remove("active");
    }
    for (focus of foci) {
        focus.style.display = "none";
    }
    if (mode === 'focus') {
        foci[slideIndex].style.display = "block";
    }
    slides[slideIndex].style.display = "flex";
    thumbs[slideIndex].classList.add("active");
    thumbs[slideIndex].scrollIntoView({ behavior: "smooth", inline: "center" });
}

function handleKey(e) {
    e ||= window.event;
    console.log(e);
    switch (e.key) {
        case "ArrowLeft":
            if (mode !== 'intro') {
                prevSlide();
            }
            break;
        case "ArrowRight":
            if (mode !== 'intro') {
                nextSlide();
            }
            break;
        case "Escape":
            setMode(mode === 'slides' ? 'intro' : 'slides');
            break;
        case " ":
        case "Enter":
            setMode(mode === 'slides' ? 'focus' : 'slides');
            break;
    }
}

function handleResize() {
    thumbs[slideIndex].scrollIntoView({ inline: "center" });
}

addEventListener("keydown", handleKey, { passive: true });
addEventListener("resize", handleResize, { passive: true });
addEventListener("DOMContentLoaded", init, { passive: true });
