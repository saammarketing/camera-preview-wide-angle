import { registerPlugin } from '@capacitor/core';

import type { CameraPreviewPlugin } from './definitions';

const CameraPreview = registerPlugin<CameraPreviewPlugin>('CameraPreview');

// Type-only re-export to avoid runtime import for types
export type {
  CameraPosition,
  CameraPreviewOptions,
  CameraPreviewPictureOptions,
  CameraSampleOptions,
  CameraPreviewFlashMode,
  CameraOpacityOptions,
  CameraPreviewPlugin
} from './definitions';
export { CameraPreview };
