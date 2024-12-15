configs = [
    {
        name: "Sierpinski Gasket",
        width: 100,
        height: 86.6,  // 100 * sqrt(3)/2
        density: 1,
        defaults: {
            zoom: { scale: 1/2, initial_colour: "black" },
        },
        items: [
            {type: 'zoom', translate: [1/4, 0]},
            {type: 'zoom', translate: [0, 1/2]},
            {type: 'zoom', translate: [1/2, 1/2]},
        ],
        filters: [
            {name: 'alpha_contrast', amount: 0.35 },
        ],
    },
    {
        name: "Sierpinski Carpet",
        width: 120,
        height: 120,
        density: 2,
        defaults: {
            zoom: { scale: 1/3, initial_colour: "black" },
        },
        items: [
            //{type: 'rect', fill: 'white'},
            {type: 'zoom', translate: [0, 0]},
            {type: 'zoom', translate: [1/3, 0]},
            {type: 'zoom', translate: [2/3, 0]},
            {type: 'zoom', translate: [0, 1/3]},
            {type: 'zoom', translate: [2/3, 1/3]},
            {type: 'zoom', translate: [0, 2/3]},
            {type: 'zoom', translate: [1/3, 2/3]},
            {type: 'zoom', translate: [2/3, 2/3]},
        ],
        filters: [
            // For some reason we get weird artifacts here (not seen in the triangle),
            // especially pronounced when canvas size is a multiple of 3.
            // The blur filter seems to fix it (TODO: try it just on the zooms).
            {name: 'blur', size: 0, quality: 4, strength: 1},
            {name: 'alpha_contrast', amount: 0.15},
        ],
    },
]

class ZoomItem {
    constructor(config) {
        this.config = config;
    }
    init(gc, renderer) {
        this.graphics = new PIXI.Graphics(gc);
        this.graphics.rect(0, 0, 1, 1).fill(this.config.initial_colour);
        this.texture = renderer.generateTexture({target: this.graphics})
        this.sprite = PIXI.Sprite.from(this.texture);
    }
    resize(width, height) {
        this.sprite.width = width * this.config.scale;
        this.sprite.height = height * this.config.scale;
        this.sprite.x = this.config.translate[0] * width;
        this.sprite.y = this.config.translate[1] * height;
    }
}

class RectItem {
    constructor(config) {
        this.config = config;
    }
    init({gc}) {
        this.graphics = PIXI.Rectangle();
        this.sprite.tint = 0xFFFFFF;
    }
    resize(width, height) {
        this.width = width;
        this.height = height;
    }
}

function makeItem(config) {
    switch (config.type) {
        case 'zoom':
            return new ZoomItem(config);
        case 'rect':
            return new RectItem(config);
        default:
            throw new Error(`Unknown item type: ${config.type}`);
    }
}

function makeFilter(config) {
    switch (config.name) {
        case 'custom': {
            const filter = new PIXI.ColorMatrixFilter();
            const a = config.amount;
            filter.matrix = [
                1.5, 0, 0, 0, -0.5,
                0, 1.5, 0, 0, -0.5,
                0, 0, 1.5, 0, -0.5,
                0, 0, 0, 255, 0
            ];
            filter.brightness(1, true);  // identity filter to apply changes
            return filter;
        }
        case 'blur': {
            return new PIXI.BlurFilter(config.size * 2 + 1, config.quality, config.strength);
        }
        case 'contrast': {
            const filter = new PIXI.ColorMatrixFilter();
            filter.contrast(config.amount);
            return filter;
        }
        case 'alpha_contrast': {
            const filter = new PIXI.ColorMatrixFilter();
            const a = config.amount;
            filter.matrix = [
                1, 0, 0, 0, 0,
                0, 1, 0, 0, 0,
                0, 0, 1, 0, 0,
                0, 0, 0, a + 1, -a / 2
            ];
            filter.brightness(1, true);  // identity filter to apply changes
            return filter;
        }
        case 'full_contrast': {
            const filter = new PIXI.ColorMatrixFilter();
            const a = config.amount;
            filter.matrix = [
                a + 1, 0, 0, 0, -a / 2,
                0, a + 1, 0, 0, -a / 2,
                0, 0, a + 1, 0, -a / 2,
                0, 0, 0, a + 1, -a / 2
            ];
            filter.brightness(1, true);  // identity filter to apply changes
            return filter;
        }
        default:
            throw new Error(`Unknown filter name: ${config.name}`);
    }
}

class Fractal {
    constructor(app, config) {
        this.app = app;
        this.config = config;
        const defaults = config.defaults;
        this.items = config.items.map(c => makeItem(Object.assign({}, defaults[c.type], c)));
        this.filters = config.filters.map(c => makeFilter(c));
        this.gc = new PIXI.GraphicsContext();
    }
    fit({width, height}) {
        const { width: w, height: h } = this.config;
        const scale = Math.min(width / w, height / h);
        return { width: Math.round(w * scale / 3) * 3 + 2, height: Math.round(h * scale / 3) * 3 + 2 };
    }
    resize() {
        const rect = this.app.canvas.parentElement.getBoundingClientRect();
        const { width, height } = this.fit(rect);
        console.log('resize', width, height);
        this.app.canvas.style.width = width+'px';
        this.app.canvas.style.height = height+'px';
        this.app.renderer.resize(width*this.config.density, height*this.config.density);
        for (const item of this.items) {
            item.resize(width*this.config.density, height*this.config.density);
        }
    }
    async init() {
        await this.app.init({ backgroundAlpha: this.config.backgroundAlpha ?? 0 });
        this.app.stage.filters = this.filters;;

        for (const item of this.items) {
            item.init(this.gc, this.app.renderer);
            this.app.stage.addChild(item.sprite);
        }
        this.app.stage.eventMode = 'static';
        this.app.stage.cursor = 'pointer';
        this.app.stage.onpointertap = () => {
            //console.log('pointertap');
            this.iterate();
        };

        // this.app.ticker.maxFPS = 2;
        // this.app.ticker.add((ticker) => {
        //     this.iterate();
        // });
    }
    iterate() {
        const t = this.app.renderer.generateTexture({
            antialias: true,
            resolution: 1,
            target: this.app.stage,
        });
        console.log('iterate', t.width, t.height);
        for (const item of this.items) {
            item.sprite.texture = t;
        }
    }
}

async function init() {
    const el = document.getElementById('arena');
    const app = new PIXI.Application({resizeTo: el});
    globalThis.__PIXI_APP__ = app;
    window.__PIXI_DEVTOOLS__ = {
        app: app,
        // If you are not using a pixi app, you can pass the renderer and stage directly
        // renderer: myRenderer,
        // stage: myStage,
      };
    const fractal = window.fractal = new Fractal(app, configs[0]);
    await fractal.init();
    el.appendChild(app.canvas);

    fractal.resize();
    addEventListener("resize", () => fractal.resize(), { passive: true });
}

addEventListener("DOMContentLoaded", init, { passive: true });
