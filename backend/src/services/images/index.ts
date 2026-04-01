import type { RequestContext } from "../../env";
import { createId } from "../../lib/ids";
import { generatePortraitWithFal } from "../../providers/fal";
import { storeRemoteImageInR2 } from "../../providers/r2";

export async function generateCharacterPortrait(context: RequestContext, prompt: string) {
  const remoteUrl = await generatePortraitWithFal(context.env, prompt);
  const key = `portraits/${context.user!.userId}/${createId("portrait")}.jpg`;
  const avatarUrl = await storeRemoteImageInR2(context.env, key, remoteUrl);
  return { avatarUrl };
}

