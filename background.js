import * as THREE from './assets/vendor/three/three.module.js';

// Decoration only: navigation and content never depend on WebGL.
try {
  const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: false, powerPreference: 'low-power' });
  renderer.setClearColor(0x000000, 0);
  renderer.domElement.className = 'code-background';
  renderer.domElement.setAttribute('aria-hidden', 'true');
  document.body.prepend(renderer.domElement);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(48, 1, 0.1, 100);
  camera.position.z = 24;
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  const pointer = new THREE.Vector2();
  const drift = new THREE.Vector2();

  const glyphs = '0137<>/{};*+';
  const atlas = document.createElement('canvas');
  atlas.width = glyphs.length * 64;
  atlas.height = 64;
  const ink = atlas.getContext('2d');
  ink.fillStyle = '#ffffff';
  ink.font = '32px monospace';
  ink.textAlign = 'center';
  ink.textBaseline = 'middle';
  [...glyphs].forEach((glyph, i) => ink.fillText(glyph, i * 64 + 32, 32));
  const texture = new THREE.CanvasTexture(atlas);
  texture.minFilter = THREE.LinearFilter;
  texture.generateMipmaps = false;

  // A regular field bends into waves; stable seeds keep page loads calm.
  const positions = [], seeds = [];
  for (let row = 0; row < 34; row++) {
    for (let col = 0; col < 64; col++) {
      positions.push((col - 31.5) * 0.75, (row - 16.5) * 0.75, 0);
      seeds.push(((col * 73 + row * 37) % 211) / 211);
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geometry.setAttribute('seed', new THREE.Float32BufferAttribute(seeds, 1));
  const uniforms = {
    time: { value: 0 }, atlas: { value: texture },
    density: { value: 1 }, pointer: { value: drift },
  };
  const material = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, uniforms,
    vertexShader: `
      attribute float seed;
      uniform float time;
      uniform float density;
      uniform vec2 pointer;
      varying float vSeed;
      varying float vFade;
      void main() {
        vec3 p = position;
        p.z = sin(p.x * .24 + time * .22) * 1.7
            + cos(p.y * .32 - time * .17) * 1.3;
        p.y += sin(p.x * .19 + time * .15) * .8;
        p.xy += pointer * (.25 + (p.z + 3.) * .09);
        vec4 view = modelViewMatrix * vec4(p, 1.);
        gl_Position = projectionMatrix * view;
        gl_PointSize = density * (21. + seed * 7.) * (24. / -view.z);
        vec2 screen = gl_Position.xy / gl_Position.w;
        float center = smoothstep(.12, .7, length(screen * vec2(1., 1.3)));
        vFade = (.035 + center * .11) * (.6 + .4 * sin(seed * 6.28 + time * .18));
        vSeed = seed;
      }
    `,
    fragmentShader: `
      uniform sampler2D atlas;
      uniform float time;
      varying float vSeed;
      varying float vFade;
      void main() {
        float glyph = mod(floor(vSeed * ${glyphs.length}. + time * .12), ${glyphs.length}.);
        vec2 uv = vec2((glyph + gl_PointCoord.x) / ${glyphs.length}., 1. - gl_PointCoord.y);
        float alpha = texture2D(atlas, uv).a * vFade;
        gl_FragColor = vec4(vec3(.24), alpha);
      }
    `,
  });
  const field = new THREE.Points(geometry, material);
  scene.add(field);

  let lastFrame = 0;
  let elapsed = 0;
  let contextLost = false;
  function render(now = 0) {
    if (contextLost) return;
    const delta = Math.min((now - lastFrame) / 1000, .05);
    if (now && now - lastFrame < 1000 / 30) return;
    lastFrame = now;
    if (!motion.matches) elapsed += Math.max(delta, 0);
    drift.lerp(motion.matches ? new THREE.Vector2() : pointer, .035);
    uniforms.time.value = elapsed;
    renderer.render(scene, camera);
  }
  function resize() {
    const ratio = Math.min(devicePixelRatio || 1, 1.5);
    renderer.setPixelRatio(ratio);
    renderer.setSize(innerWidth, innerHeight);
    camera.aspect = innerWidth / innerHeight;
    camera.updateProjectionMatrix();
    uniforms.density.value = ratio * (innerWidth < 700 ? .8 : 1);
    lastFrame = 0;
    render();
  }
  function playback() {
    lastFrame = 0;
    renderer.setAnimationLoop(document.hidden || motion.matches || contextLost ? null : render);
    if (!document.hidden) render();
  }
  addEventListener('pointermove', event => {
    if (event.pointerType !== 'mouse' || motion.matches) return;
    pointer.set(event.clientX / innerWidth * 2 - 1, 1 - event.clientY / innerHeight * 2);
  }, { passive: true });
  document.addEventListener('pointerleave', () => pointer.set(0, 0));
  addEventListener('resize', resize, { passive: true });
  document.addEventListener('visibilitychange', playback);
  motion.addEventListener('change', playback);
  renderer.domElement.addEventListener('webglcontextlost', event => {
    event.preventDefault();
    contextLost = true;
    playback();
  });
  renderer.domElement.addEventListener('webglcontextrestored', () => {
    contextLost = false;
    resize();
    playback();
  });
  resize();
  playback();
} catch {
  // A plain background is the fallback for unsupported or disabled WebGL.
  document.querySelector('.code-background')?.remove();
}
