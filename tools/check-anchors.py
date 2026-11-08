#!/usr/bin/env python3
"""校验 QuietWeibo 的 Hook 锚点在微博 DEX 中是否存在。

用法：
    python3 tools/check-anchors.py <dex 文件或目录> [...]

输入为微博 base.apk 里的 classes*.dex，或 r0dump 还原出的应用 DEX
（000030-000050 这 21 个；GMS/WebView 的 blob 会被自动跳过或 WARN）。

输出 OK / MISS。必需锚点缺失时退出码 1；可选锚点（同广告位的第二种实现）缺失只提示。
只依赖 Python 标准库，不读取、不执行目标 DEX 中的任何代码。
"""
import os
import struct
import sys


def uleb(b, off):
    result = shift = 0
    while True:
        x = b[off]
        off += 1
        result |= (x & 0x7f) << shift
        shift += 7
        if not x & 0x80:
            return result, off


def read_strings(b, header):
    count, off = struct.unpack('<2I', header[56:64])
    out = []
    for i in range(count):
        start = struct.unpack('<I', b[off + 4 * i:off + 4 * i + 4])[0]
        p = start
        while b[p] & 0x80:
            p += 1
        p += 1
        end = b.index(b'\0', p)
        out.append(b[p:end].decode('utf-8', 'replace'))
    return out


def parse_dex(path):
    with open(path, 'rb') as f:
        b = f.read()
    if b[:4] != b'dex\n':
        raise ValueError('not a standard dex: ' + path)
    header = b[:112]
    strings = read_strings(b, header)
    type_count, type_off = struct.unpack('<2I', header[64:72])
    proto_count, proto_off = struct.unpack('<2I', header[72:80])
    method_count, method_off = struct.unpack('<2I', header[88:96])
    types = [struct.unpack('<I', b[type_off + 4 * i:type_off + 4 * i + 4])[0]
             for i in range(type_count)]

    def type_name(idx):
        return strings[types[idx]]

    def proto(index):
        off = proto_off + 12 * index
        ret = type_name(struct.unpack('<I', b[off + 4:off + 8])[0])
        params_off = struct.unpack('<I', b[off + 8:off + 12])[0]
        params = []
        if params_off:
            n = struct.unpack('<I', b[params_off:params_off + 4])[0]
            for k in range(n):
                t = struct.unpack('<H', b[params_off + 4 + 2 * k:params_off + 6 + 2 * k])[0]
                params.append(type_name(t))
        return '(' + ''.join(params) + ')' + ret

    methods = set()
    for i in range(method_count):
        off = method_off + 8 * i
        cls = type_name(struct.unpack('<H', b[off:off + 2])[0])
        proto_idx = struct.unpack('<H', b[off + 2:off + 4])[0]
        name = strings[struct.unpack('<I', b[off + 4:off + 8])[0]]
        methods.add((cls, name, proto(proto_idx)))
    return methods


def clean(desc):
    return desc[1:-1] if desc.startswith('L') and desc.endswith(';') else desc


# (class, method, 说明, 是否必需)
ANCHORS = [
    # AD-01 冷启动开屏
    ('com/sina/weibo/startup/actor/a/b', 'b', 'AD-01 BizSplashActor 加载开屏', True),
    ('com/sina/weibo/mobileads/view/FlashAd', 'loadAndShowAd', 'AD-01 直接加载并展示', True),
    # AD-02 热启动开屏
    ('com/sina/weibo/utils/cd', 'a', 'AD-02 回到前台开屏判定/展示', True),
    ('com/sina/weibo/utils/cd', 'b', 'AD-02 后台开屏预加载', True),
    ('com/sina/weibo/utils/cd', 'c', 'AD-02 开屏广告位注册', True),
    # AD-03 信息流广告
    ('com/sina/weibo/flow/model/a', 'getItems', 'AD-03 流式列表数据出口', True),
    ('com/sina/weibo/models/Status', 'isAd', 'AD-03 广告微博标记', True),
    ('com/sina/weibo/models/Status', 'getPromotion', 'AD-03 推广单', True),
    ('com/sina/weibo/models/Promotion', 'isInvalid', 'AD-03 推广单有效性', True),
    ('com/sina/weibo/models/Status', 'getAdObject', 'AD-03 广告物料', True),
    ('com/sina/weibo/card/model/PageCardInfo', 'isAd', 'AD-03 广告卡片标记', True),
    ('com/sina/weibo/models/gson/typeadapter/FeedFlowListTypeAdapter', 'doRead',
     'AD-03 信息流解析入口', True),
    ('com/sina/weibo/models/CardList', 'getCardList', 'AD-03 卡片列表', True),
    ('com/sina/weibo/models/CardList', 'getCardMblogStatusList', 'AD-03 卡片微博列表', True),
    ('com/sina/weibo/models/CardList', 'getHotwordList', 'AD-03/07 热搜推广词', True),
    ('com/sina/weibo/models/SearchHotword', 'getPromotion', 'AD-03/07 推广词标记', True),
    ('com/sina/weibo/models/JsonCommentList', 'initFromJsonObject', 'AD-03 评论流解析', True),
    ('com/sina/weibo/models/JsonComment', 'getExtraDesc', 'AD-03 评论广告标记', True),
    ('com/sina/weibo/feed/detailrefactor/flow/h/b', 'getData',
     'AD-03 评论趋势卡数据', True),
    ('com/sina/weibo/feed/detailrefactor/flow/h/b$a', 'c',
     'AD-03 评论趋势卡 adType', True),
    ('com/sina/weibo/feed/detailrefactor/flow/comment/b', 'getData',
     'AD-03 评论项数据', True),
    ('com/sina/weibo/streamservice/adapter/BaseAdapterWrapper', 'collectAdapterDatas',
     'AD-03 最终 adapter 兜底', True),
    ('com/sina/weibo/feed/interact/flow/service/data/InteractPageFlowFetchService', 'd',
     'AD-03 正文流数据', True),
    ('com/sina/weibo/feed/detailrefactor/flow/core/DetailPageFlowFetchService', 'b',
     'AD-03 正文流预加载', True),
    ('com/sina/weibo/card/model/CardGrid', 'getGridItems', 'AD-03/07 热搜网格', True),
    ('com/sina/weibo/card/model/CardGridItem', 'getAdPromotion', 'AD-03/07 推广热搜', True),
    ('com/sina/weibo/card/model/CardMarqueeAlpha', 'getAdTag', 'AD-07 通栏广告卡', True),
    ('com/sina/weibo/card/model/CardSpliceMultiple', 'getAdTag', 'AD-07 拼图广告', True),
    ('com/sina/weibo/card/model/CardSpliceMultipleItem', 'getPromotion', 'AD-07 拼图子卡', True),
    ('com/sina/weibo/card/model/Card285Info', 'getImageInfo', 'AD-07 Card285 广告', True),
    ('com/sina/weibo/card/model/Card285Info$CoverItem', 'getAdTag', 'AD-07 封面广告标记', True),
    ('com/sina/weibo/card/model/CardGroup', 'getCardsList', 'AD-03 卡片组', True),
    # AD-04 首页顶导广告
    ('com/sina/weibo/feedv2/home/service/ad/AdChannelService', 'onInit',
     'AD-04 顶导广告服务', True),
    ('com/sina/weibo/page/profilev2/container/service/ProfileAdDialogService', 'onInit',
     'AD-06 个人页广告弹窗', False),
    ('com/sina/weibo/supergroup/page/l', 'checkAndShowAdAsync',
     'AD-06 超话页广告弹窗', False),
    ('com/sina/weibo/page/ad/c', 'a', 'AD-06 图片广告 Alert', False),
    # AD-05 下拉悬停广告
    ('com/sina/weibo/feedv2/stream/service/ad/AdPullPauseOperatorServiceV2', 'onInit',
     'AD-05 首页下拉悬停', True),
    ('com/sina/weibo/feedv2/stream/service/ad/DiscoverAdPullPauseOperatorServiceV2',
     'onInit', 'AD-05 发现页下拉悬停', False),
    # AD-06 大视窗/浮层/弹窗
    ('com/sina/weibo/feedv2/home/service/hometopvision/HomeAdTopVisionService', 'onInit',
     'AD-06 首页大视窗', True),
    ('com/sina/weibo/feedv2/stream/service/streamtopvision/StreamAdInsertService', 'onInit',
     'AD-06 流内大视窗插入', True),
    ('com/sina/weibo/feedv2/stream/service/ad/homgdialog/HomeZoomAdDialogService', 'onInit',
     'AD-06 首页广告弹窗', False),
    ('com/sina/weibo/feedv2/stream/service/ad/AdVisionFloatAnimateService', 'onInitView',
     'AD-06 广告浮层动画', False),
    ('com/sina/weibo/feedv2/stream/service/ad/anim/HomeZoomAdAnimServise', 'onInitView',
     'AD-06 大视窗动画', False),
    # AD-07 发现页广告
    ('com/sina/weibo/feedv2/discover2/service/DiscoverFlowAdService', 'onInit',
     'AD-07 发现页广告流', True),
    ('com/sina/weibo/page/searchv2/container/service/advertise/DiscoverAdService',
     'onInit', 'AD-07 发现页搜索广告', False),
    ('com/sina/weibo/biz/discover/d', 'show', 'AD-07 发现页广告弹窗', True),
    # AD-08 搜索与榜单广告
    ('com/sina/weibo/page/searchv2/container/service/advertise/SearchAdService', 'onInit',
     'AD-08 搜索广告服务', True),
    ('com/sina/weibo/search/rank/channel/service/ad/RankAdService', 'onInitSafe',
     'AD-08 榜单广告服务', False),
    # AD-09 退屏/切页广告
    ('com/sina/weibo/feedv2/biz/service/AdFeedExitWindowService', 'onInitView',
     'AD-09 退屏窗口广告', True),
    ('com/sina/weibo/feedv2/biz/service/AdFeedStreamService', 'onInitView',
     'AD-09 广告流服务', False),
    # AD-10 广告重排
    ('com/sina/weibo/feedv2/flow/services/adreorder/AdReOrderService', 'onInitView',
     'AD-10 广告重排服务', True),
    ('com/sina/weibo/feed/detailrefactor/service/adreorder/DetailAdReOrderService',
     'onInitView', 'AD-10 正文页广告重排', False),
    # AD-11 视频悬浮广告
    ('com/sina/weibo/biz/e', 'a', 'AD-11 视频悬浮 View（混淆名）', True),
    ('com/sina/weibo/extlibui/floatview/FloatViewFactory', 'getFloatView',
     'AD-11 广告浮窗类型', True),
    ('com/sina/weibo/extlibui/floatview/FloatViewFactory', 'getFloatViewModel',
     'AD-11 广告浮窗 model', True),
    ('com/sina/weibo/floatvideo/b', 'j', 'AD-11 悬浮动画入口（混淆名）', False),
    # AD-12 摇一摇/扭一扭
    ('com/sina/weibo/mobileads/util/f', 'f', 'AD-12 摇一摇注册', True),
    ('com/sina/weibo/mobileads/util/e', 'h', 'AD-12 摇一摇 Plus 注册', True),
    ('com/sina/weibo/mobileads/util/g', 'a', 'AD-12 扭一扭注册', True),
    # AD-13 广告预加载/策略刷新
    ('com/sina/weibo/mobileads/controller/RefreshService', 'startFetchData',
     'AD-13 广告配置/物料拉取', True),
]


def collect(paths):
    files = []
    for path in paths:
        if os.path.isdir(path):
            for name in sorted(os.listdir(path)):
                if name.endswith(('.dex', '.raw')):
                    files.append(os.path.join(path, name))
        else:
            files.append(path)
    return files


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    methods = {}
    for path in collect(argv[1:]):
        try:
            for cls, name, proto in parse_dex(path):
                methods.setdefault((clean(cls), name), set()).add(proto)
        except Exception as exc:  # 跳过非标准/损坏文件，不影响其余 DEX
            print('WARN skip %s: %s' % (path, exc))
    missing_required = 0
    for cls, name, desc, required in ANCHORS:
        hits = methods.get((cls, name))
        if hits:
            print('OK   %-70s %-28s %s' % (cls.replace('/', '.') + '#' + name,
                                           desc, ' | '.join(sorted(hits))))
        else:
            print('%s %-70s %s' % ('MISS' if required else 'OPT ',
                                   cls.replace('/', '.') + '#' + name, desc))
            if required:
                missing_required += 1
    print('\n%d anchors, %d required missing' % (len(ANCHORS), missing_required))
    return 1 if missing_required else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
