import type { RequestContext } from "../../env";
import { createId } from "../../lib/ids";
import { generateChatBackgroundWithFal, generatePortraitWithFal } from "../../providers/fal";
import { storeRemoteImageInR2 } from "../../providers/r2";

export async function generateCharacterPortrait(context: RequestContext, prompt: string) {
  const remoteUrl = await generatePortraitWithFal(
    context.env,
    [
      "Square full-bleed character portrait that fills the entire image frame.",
      "Do not make a circular avatar, round crop, badge, medallion, border, or framed icon.",
      prompt
    ].join("\n")
  );
  const key = `portraits/${context.user!.userId}/${createId("portrait")}.jpg`;
  const avatarUrl = await storeRemoteImageInR2(context.env, key, remoteUrl);
  return { avatarUrl };
}

export async function generateChatBackground(context: RequestContext, prompt: string) {
  const remoteUrl = await generateChatBackgroundWithFal(context.env, prompt);
  const key = `chat-backgrounds/${context.user!.userId}/${createId("background")}.jpg`;
  const imageUrl = await storeRemoteImageInR2(context.env, key, remoteUrl);
  return { imageUrl };
}
