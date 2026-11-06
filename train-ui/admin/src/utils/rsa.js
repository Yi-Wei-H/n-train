import JSEncrypt from 'jsencrypt'
import request from './request'

/**
 * 取 RSA 公钥并加密密码（密码明文不上网络）：
 * 每次登录都取新钥，后端私钥 2 分钟内一次性使用。
 * 返回 { keyId, cipher }
 */
export async function rsaEncrypt(password) {
  const { keyId, publicKey } = await request.get('/auth/public-key')
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(publicKey)
  const cipher = encryptor.encrypt(password)
  if (!cipher) {
    throw new Error('密码加密失败，请重试')
  }
  return { keyId, cipher }
}
