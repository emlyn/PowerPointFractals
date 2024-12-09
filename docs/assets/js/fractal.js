function canvasSize() {
    const el = document.getElementById('canvas');
    const bounds = el.getBoundingClientRect();
    return Math.min(bounds.width, bounds.height);
}

function handleResize() {
    console.log('resize', canvasSize());
    let size = canvasSize();
    app.canvas.style.width = size+'px';
    app.canvas.style.height = size+'px';
    size *= 2;
    window.app.renderer.resize(size, size);
    // const graphics = window.graphics;
    // graphics.rect(0, 0, size, size);
    // graphics.fill(0x800000);
    // const texture = app.renderer.generateTexture({target: graphics});

    const pos = [[size/4, 0], [0, size/2], [size/2, size/2]];
    for (let i = 0; i < 3; i++) {
        const s = window.sprites[i];
        s.width = size/2;
        s.height = size/2;
        s.x = pos[i][0];
        s.y = pos[i][1];
        // s.texture = texture;
    }
}

async function init() {
    const app = window.app = new PIXI.Application();
    await app.init({ backgroundAlpha: 0.05 })
    document.getElementById('canvas').appendChild(app.canvas);

    const graphics = window.graphics = new PIXI.Graphics();
    graphics.rect(0, 0, 1, 1);
    graphics.fill('#000');
    const texture = app.renderer.generateTexture({target: graphics});

    const filter = window.filter = new PIXI.ColorMatrixFilter();
    filter.matrix = [
        1, 0, 0, 0, 0,
        0, 1, 0, 0, 0,
        0, 0, 1, 0, 0,
        0, 0, 0, 0.4 + 1, -0.4 / 2
    ];
    filter.brightness(1, true);  // identity filter to apply changes
    app.stage.filters = [filter];

    const sprites = window.sprites = [];
    for (let i = 0; i < 3; i++) {
        const spr = PIXI.Sprite.from(texture);
        app.stage.addChild(spr);
        sprites.push(spr);
    }

    handleResize();

    // app.stage.eventMode = 'static';
    // app.stage.cursor = 'pointer';

    // app.stage.onpointertap = () => {
    //     //console.log('pointertap');
    //     const t = app.renderer.generateTexture({target: app.stage});
    //     for (const s of sprites) {
    //         s.texture = t;
    //     }
    // };

    // app.ticker.maxFPS = 20;
    app.ticker.add((ticker) => {
        const t = app.renderer.generateTexture({target: app.stage});
        for (const s of sprites) {
            s.texture = t;
        }
    });
}

addEventListener("DOMContentLoaded", init, { passive: true });
addEventListener("resize", handleResize, { passive: true });
