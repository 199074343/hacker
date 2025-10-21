/**
 * 微信分享配置
 *
 * 使用说明：
 * 1. 需要微信公众号并配置JS接口安全域名
 * 2. 需要后端提供签名接口（返回appId, timestamp, nonceStr, signature）
 * 3. 将下面的配置信息替换为实际值
 */

// 分享配置信息
const SHARE_CONFIG = {
    title: '高顿GDTech第八届骇客大赛',
    desc: '教育XAI Coding',
    link: window.location.href.split('#')[0],  // 去掉#及其后面的内容
    imgUrl: window.location.origin + '/banner.png'  // 使用banner.png作为分享图
};

/**
 * 初始化微信分享
 */
function initWechatShare() {
    // 检查是否在微信环境中
    const isWechat = /micromessenger/i.test(navigator.userAgent);

    if (!isWechat) {
        console.log('非微信环境，跳过微信分享配置');
        return;
    }

    console.log('检测到微信环境，初始化分享配置...');
    console.log('分享配置:', SHARE_CONFIG);

    // 从后端获取微信签名配置
    fetchWechatSignature();
}

/**
 * 从后端获取微信签名配置
 */
async function fetchWechatSignature() {
    try {
        // 获取当前页面URL（去掉#及其后面的内容）
        const url = window.location.href.split('#')[0];

        // 调用后端签名接口
        const apiUrl = API_BASE_URL + '/hackathon/wechat/signature?url=' + encodeURIComponent(url);
        console.log('请求微信签名接口:', apiUrl);

        const response = await fetch(apiUrl);
        const result = await response.json();

        console.log('微信签名响应:', result);

        if (result.code === 200 && result.data) {
            configWechatShare(result.data);
        } else {
            console.error('获取微信签名失败:', result.message || '未知错误');
        }
    } catch (error) {
        console.error('获取微信签名异常:', error);
    }
}

/**
 * 配置微信分享
 * @param {Object} wxConfig - 微信配置 {appId, timestamp, nonceStr, signature}
 */
function configWechatShare(wxConfig) {
    if (typeof wx === 'undefined') {
        console.error('微信JS-SDK未加载');
        return;
    }

    // 配置微信JS-SDK
    wx.config({
        debug: false,  // 开发环境可设为true，生产环境设为false
        appId: wxConfig.appId,
        timestamp: wxConfig.timestamp,
        nonceStr: wxConfig.nonceStr,
        signature: wxConfig.signature,
        jsApiList: [
            'updateAppMessageShareData',  // 分享给朋友
            'updateTimelineShareData',     // 分享到朋友圈
            'onMenuShareAppMessage',       // 旧版分享给朋友（兼容）
            'onMenuShareTimeline'          // 旧版分享到朋友圈（兼容）
        ]
    });

    // 配置成功回调
    wx.ready(function() {
        console.log('微信JS-SDK配置成功');

        // 新版API：分享给朋友
        wx.updateAppMessageShareData({
            title: SHARE_CONFIG.title,
            desc: SHARE_CONFIG.desc,
            link: SHARE_CONFIG.link,
            imgUrl: SHARE_CONFIG.imgUrl,
            success: function() {
                console.log('分享给朋友配置成功');
            },
            fail: function(err) {
                console.error('分享给朋友配置失败:', err);
            }
        });

        // 新版API：分享到朋友圈
        wx.updateTimelineShareData({
            title: SHARE_CONFIG.title,
            link: SHARE_CONFIG.link,
            imgUrl: SHARE_CONFIG.imgUrl,
            success: function() {
                console.log('分享到朋友圈配置成功');
            },
            fail: function(err) {
                console.error('分享到朋友圈配置失败:', err);
            }
        });

        // 兼容旧版API
        wx.onMenuShareAppMessage({
            title: SHARE_CONFIG.title,
            desc: SHARE_CONFIG.desc,
            link: SHARE_CONFIG.link,
            imgUrl: SHARE_CONFIG.imgUrl,
            success: function() {
                console.log('分享成功（旧版API）');
            }
        });

        wx.onMenuShareTimeline({
            title: SHARE_CONFIG.title,
            link: SHARE_CONFIG.link,
            imgUrl: SHARE_CONFIG.imgUrl,
            success: function() {
                console.log('分享成功（旧版API）');
            }
        });
    });

    // 配置失败回调
    wx.error(function(res) {
        console.error('微信JS-SDK配置失败:', res);
    });
}

// 页面加载完成后初始化
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initWechatShare);
} else {
    initWechatShare();
}
