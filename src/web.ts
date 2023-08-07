import { WebPlugin } from '@capacitor/core';

import type { MinimizePlugin } from './definitions';

export class MinimizeWeb extends WebPlugin implements MinimizePlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
