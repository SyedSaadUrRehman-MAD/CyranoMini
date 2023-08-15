import { registerPlugin } from '@capacitor/core';

import type { MinimizePlugin } from './definitions';

const Minimize = registerPlugin<MinimizePlugin>('Minimize');

export * from './definitions';
export { Minimize };
