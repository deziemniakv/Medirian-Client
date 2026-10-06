// Lets `node --experimental-strip-types` load the launcher's TypeScript sources, whose relative
// imports have no extension (the bundler adds it): tries "<specifier>.ts" when the plain one fails.
import { registerHooks } from 'node:module';

registerHooks({
  resolve(specifier, context, nextResolve) {
    try {
      return nextResolve(specifier, context);
    } catch (error) {
      if ((specifier.startsWith('.') || specifier.startsWith('file:')) && !/\.[cm]?[jt]s$/.test(specifier)) {
        return nextResolve(specifier + '.ts', context);
      }
      throw error;
    }
  }
});
