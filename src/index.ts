import { registerPlugin } from '@capacitor/core';

import type { MinimizePlugin } from './definitions';

const Minimize = registerPlugin<MinimizePlugin>('Minimize', {
  web: () => import('./web').then(m => new m.MinimizeWeb()),
});

export * from './definitions';
export { Minimize };
