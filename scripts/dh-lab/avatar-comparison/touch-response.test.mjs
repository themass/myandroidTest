import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chooseRegion, springStep } from './touch-response.mjs';

test('only a nearby supported joint is selected', () => {
  assert.equal(chooseRegion({head: 0.04, hand: 0.3}, 0.12), 'head');
  assert.equal(chooseRegion({head: 0.3, hand: 0.05}, 0.12), 'hand');
  assert.equal(chooseRegion({head: 0.3, hand: 0.4}, 0.12), null);
});
test('spring settles after release and clamps long frame intervals', () => {
  let state = {value: 0, velocity: 0};
  for (let i = 0; i < 60; i++) state = springStep(state, 1, 1 / 60);
  assert.ok(state.value > 0.95 && state.value < 1.05);
  for (let i = 0; i < 180; i++) state = springStep(state, 0, 1 / 60);
  assert.ok(Math.abs(state.value) < 0.001);
  assert.deepEqual(springStep({value: 0, velocity: 0}, 1, 4), springStep({value: 0, velocity: 0}, 1, 0.05));
});
