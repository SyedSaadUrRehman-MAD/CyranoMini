export interface MinimizePlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  minimize(): Promise<any>;
}
