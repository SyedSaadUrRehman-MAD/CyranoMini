export interface MinimizePlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
