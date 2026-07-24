import type { RequestContext } from "../../env";
import { publicAssetUrl } from "../../lib/assets";
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

export async function generateChatBackground(
  context: RequestContext,
  prompt: string,
  requestKey: string | null = null
) {
  const stableName = requestKey
    ?.replace(/[^a-zA-Z0-9._-]/g, "_")
    .replace(/^[_\.]+|[_\.]+$/g, "")
    .slice(0, 180);
  const key = stableName
    ? `chat-backgrounds/${context.user!.userId}/${stableName}.jpg`
    : `chat-backgrounds/${context.user!.userId}/${createId("background")}.jpg`;

  if (stableName && await context.env.ASSETS.head(key)) {
    return { imageUrl: publicAssetUrl(context.env.R2_PUBLIC_BASE_URL, key) };
  }

  const remoteUrl = await generateChatBackgroundWithFal(context.env, prompt);
  const imageUrl = await storeRemoteImageInR2(context.env, key, remoteUrl);
  return { imageUrl };
}
