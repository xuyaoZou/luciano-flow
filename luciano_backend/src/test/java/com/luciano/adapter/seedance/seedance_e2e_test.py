#!/usr/bin/env python3
"""
Seedance/Seedream 适配器 E2E 测试脚本
按照官方 API 文档格式直接调用火山方舟接口，验证各能力的请求格式和响应解析。

测试顺序（由简到繁）：
  1. T2I 文生图（同步返回，最快验证）
  2. T2V 文生视频（异步任务）
  3. I2V 图生视频-首帧（需要图片 URL）
  4. I2V 多图参考（2.0 多模态参考）
  5. FLF 首尾帧
  6. RTV 多模态参考生成
  7. VIDEO_EDIT 视频编辑
  8. VIDEO_EXTEND 视频延长

使用方法：
  python3 seedance_e2e_test.py [test_name]

  不传参数则依次执行所有测试。
  指定 test_name 只跑单个测试，如：python3 seedance_e2e_test.py t2i

依赖：pip3 install requests
"""

import json
import time
import sys
import os
from datetime import datetime

import requests

# ==================== 配置 ====================

API_KEY = os.environ.get("ARK_API_KEY", "ark-7e05ed56-f109-434e-b3ef-0c2a276e4f56-3d070")
BASE_URL = "https://ark.cn-beijing.volces.com"

# 视频模型
MODEL_SD20 = "doubao-seedance-2-0-260128"
MODEL_SD20_FAST = "doubao-seedance-2-0-fast-260128"
MODEL_SD15_PRO = "doubao-seedance-1-5-pro-251215"
MODEL_SD10_PRO = "doubao-seedance-1-0-pro-250528"

# 图片模型
MODEL_SEEDREAM_50 = "doubao-seedream-5-0-260128"
MODEL_SEEDREAM_45 = "doubao-seedream-4-5-251128"
MODEL_SEEDREAM_40 = "doubao-seedream-4-0-250828"

# 测试用图片 URL（使用公开可访问的图片）
# 用于 I2V / FLF / RTV / T2I 参考图 / VIDEO_EDIT
# 测试用图片 URL — 从 e2e_results/t2i.json 动态加载（T2I 生成的图片，24h 有效）
# 如果没有则用占位 URL（可能不可用，相关测试会失败）
_T2I_RESULT_PATH = os.path.join(os.path.dirname(__file__), "e2e_results", "t2i.json")
_DEFAULT_IMAGE_1 = "https://ark-acg-cn-beijing.tos-cn-beijing.volces.com/doubao-seedream-5-0/02178192522492898c470d32e326dd7109413e831d170d8d72189_0.png"
_DEFAULT_IMAGE_2 = "https://ark-acg-cn-beijing.tos-cn-beijing.volces.com/doubao-seedream-5-0/021781925187289275a44fd862e8b57dcd33414ced19696334586_0.png"

def _load_test_image():
    """从 T2I 测试结果加载图片 URL"""
    if os.path.exists(_T2I_RESULT_PATH):
        try:
            with open(_T2I_RESULT_PATH, "r") as f:
                data = json.load(f)
                images = data.get("submit_response", {}).get("data", [])
                if images and isinstance(images, list):
                    return images[0].get("url", _DEFAULT_IMAGE_1)
        except:
            pass
    return _DEFAULT_IMAGE_1

TEST_IMAGE_1 = _load_test_image()

# 第二张图：从 T2I 参考图测试结果加载
_T2I_REF_RESULT_PATH = os.path.join(os.path.dirname(__file__), "e2e_results", "t2i_with_reference.json")
def _load_test_image_2():
    if os.path.exists(_T2I_REF_RESULT_PATH):
        try:
            with open(_T2I_REF_RESULT_PATH, "r") as f:
                data = json.load(f)
                images = data.get("submit_response", {}).get("data", [])
                if images and isinstance(images, list):
                    return images[0].get("url", _DEFAULT_IMAGE_1)
        except:
            pass
    return _DEFAULT_IMAGE_1

TEST_IMAGE_2 = _load_test_image_2()  # 第二张图用 T2I 参考图生成的图片

# 超时配置
POLL_TIMEOUT = 600  # 视频任务轮询超时（秒）
POLL_INTERVAL = 9  # 轮询间隔（秒）

# 结果保存目录
RESULTS_DIR = os.path.join(os.path.dirname(__file__), "e2e_results")

# ==================== 通用函数 ====================

def auth_headers():
    return {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json",
    }


def post_task(path, body):
    """提交异步任务"""
    url = BASE_URL + path
    print(f"\nPOST {url}")
    print(f"Body: {json.dumps(body, ensure_ascii=False, indent=2)[:500]}")
    resp = requests.post(url, headers=auth_headers(), json=body, timeout=30)
    print(f"HTTP Status: {resp.status_code}")
    try:
        result = resp.json()
        print(f"Response: {json.dumps(result, ensure_ascii=False, indent=2)[:800]}")
    except:
        print(f"Response (raw): {resp.text[:500]}")
        result = {"raw": resp.text}
    return resp.status_code, result


def post_sync(path, body):
    """同步请求（图片生成）"""
    url = BASE_URL + path
    print(f"\nPOST {url}")
    print(f"Body: {json.dumps(body, ensure_ascii=False, indent=2)[:500]}")
    resp = requests.post(url, headers=auth_headers(), json=body, timeout=300)
    print(f"HTTP Status: {resp.status_code}")
    try:
        result = resp.json()
        print(f"Response: {json.dumps(result, ensure_ascii=False, indent=2)[:1000]}")
    except:
        print(f"Response (raw): {resp.text[:500]}")
        result = {"raw": resp.text}
    return resp.status_code, result


def get_task(task_id):
    """查询任务状态"""
    url = f"{BASE_URL}/api/v3/contents/generations/tasks/{task_id}"
    resp = requests.get(url, headers=auth_headers(), timeout=30)
    return resp.status_code, resp.json() if resp.status_code == 200 else {"raw": resp.text}


def poll_task(task_id, max_wait=POLL_TIMEOUT, interval=POLL_INTERVAL):
    """轮询异步任务直到完成"""
    print(f"\n🔄 轮询任务 {task_id}（超时 {max_wait}s，间隔 {interval}s）")
    start = time.time()

    while time.time() - start < max_wait:
        try:
            status_code, result = get_task(task_id)
            status = result.get("status", "unknown")
            elapsed = int(time.time() - start)

            if status == "running":
                print(f"  [{elapsed}s] running...")
            elif status == "queued":
                print(f"  [{elapsed}s] queued...")
            elif status == "succeeded":
                print(f"\n✅ 任务完成！（{elapsed}s）")
                # 提取视频/图片 URL
                content = result.get("content", {})
                if isinstance(content, dict):
                    video_url = content.get("video_url", "")
                    if video_url:
                        print(f"📹 视频 URL: {video_url[:120]}...")
                    # 尾帧
                    last_frame = content.get("last_frame_url", "")
                    if last_frame:
                        print(f"🖼️ 尾帧 URL: {last_frame[:120]}...")
                print(f"\nFull Response:\n{json.dumps(result, ensure_ascii=False, indent=2)[:3000]}")
                return result
            elif status == "failed":
                error = result.get("error", {})
                print(f"\n❌ 任务失败: {error}")
                print(f"Full Response:\n{json.dumps(result, ensure_ascii=False, indent=2)[:2000]}")
                return result
            elif status == "expired":
                print(f"\n⏰ 任务过期")
                return result
            else:
                print(f"  [{elapsed}s] status={status}")
        except Exception as e:
            print(f"  轮询异常: {e}")

        time.sleep(interval)

    print(f"\n⏰ 轮询超时（{max_wait}s）")
    return None


def save_result(test_name, request_body, response, poll_result=None):
    """保存测试结果到文件"""
    os.makedirs(RESULTS_DIR, exist_ok=True)
    result = {
        "test": test_name,
        "timestamp": datetime.now().isoformat(),
        "request": request_body,
        "submit_response": response,
        "poll_result": poll_result,
    }
    filepath = os.path.join(RESULTS_DIR, f"{test_name}.json")
    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"\n💾 结果已保存: {filepath}")


def print_separator(title):
    print("\n" + "=" * 60)
    print(f"  {title}")
    print("=" * 60)


# ==================== 测试用例 ====================

def test_t2i():
    """
    T2I: 文生图（Seedream 5.0 lite）
    官方 API: POST /api/v3/images/generations
    同步返回，不需要轮询。
    """
    print_separator("T2I: 文生图（Seedream 5.0 lite）")

    body = {
        "model": MODEL_SEEDREAM_50,
        "prompt": "一只可爱的橘猫坐在窗台上，阳光洒在身上，温馨治愈风格",
        "size": "2K",
        "output_format": "png",
        "watermark": False,
    }

    status, result = post_sync("/api/v3/images/generations", body)

    success = status == 200
    if success:
        # 提取图片 URL — 官方返回 data 是数组，每项有 url/size
        data = result.get("data", [])
        if isinstance(data, list):
            images = data
        elif isinstance(data, dict):
            images = data.get("images", [])
        else:
            images = []
        if images:
            for i, img in enumerate(images):
                url = img.get("url", "") if isinstance(img, dict) else str(img)
                print(f"🖼️ 图片 {i+1} URL: {url[:120]}...")
        print("\n✅ T2I 通过")
    else:
        print(f"\n❌ T2I 失败 (HTTP {status})")

    save_result("t2i", body, result)
    return success


def test_t2i_with_reference():
    """
    T2I + 参考图: 图文生图（多图融合）
    官方 API: image 参数支持 string（单图）或 array（多图）
    """
    print_separator("T2I + 参考图: 多图融合")

    body = {
        "model": MODEL_SEEDREAM_50,
        "prompt": "将图片的风格融合，生成一张新的艺术画作",
        "image": TEST_IMAGE_1,  # 单图参考（用 T2I 生成的有效 URL）
        "size": "2K",
        "output_format": "png",
        "watermark": False,
    }

    status, result = post_sync("/api/v3/images/generations", body)

    success = status == 200
    if success:
        data = result.get("data", [])
        if isinstance(data, list):
            images = data
        elif isinstance(data, dict):
            images = data.get("images", [])
        else:
            images = []
        if images:
            for i, img in enumerate(images):
                url = img.get("url", "") if isinstance(img, dict) else str(img)
                print(f"🖼️ 图片 {i+1} URL: {url[:120]}...")
        print("\n✅ T2I 参考图通过")
    else:
        print(f"\n❌ T2I 参考图失败 (HTTP {status})")

    save_result("t2i_with_reference", body, result)
    return success


def test_t2i_sequential():
    """
    T2I 组图: sequential_image_generation
    官方 API: sequential_image_generation: "auto" + max_images
    """
    print_separator("T2I 组图: sequential_image_generation")

    body = {
        "model": MODEL_SEEDREAM_50,
        "prompt": "一只猫咪的一天：早上起床、吃早餐、晒太阳、玩耍、睡觉",
        "sequential_image_generation": "auto",
        "sequential_image_generation_options": {"max_images": 4},
        "size": "2K",
        "output_format": "png",
        "watermark": False,
    }

    status, result = post_sync("/api/v3/images/generations", body)

    success = status == 200
    if success:
        data = result.get("data", [])
        if isinstance(data, list):
            images = data
        elif isinstance(data, dict):
            images = data.get("images", [])
        else:
            images = []
        print(f"📸 组图数量: {len(images)}")
        for i, img in enumerate(images):
            url = img.get("url", "") if isinstance(img, dict) else str(img)
            print(f"  图片 {i+1}: {url[:120]}...")
        print("\n✅ T2I 组图通过")
    else:
        print(f"\n❌ T2I 组图失败 (HTTP {status})")

    save_result("t2i_sequential", body, result)
    return success


def test_t2v():
    """
    T2V: 文生视频（Seedance 2.0）
    官方 API: POST /api/v3/contents/generations/tasks
    异步任务，需要轮询。
    """
    print_separator("T2V: 文生视频（Seedance 2.0）")

    body = {
        "model": MODEL_SD20,
        "content": [
            {"type": "text", "text": "一只橘猫在草地上奔跑，阳光明媚，高清画质"}
        ],
        "ratio": "16:9",
        "duration": 5,
        "resolution": "720p",
        "watermark": False,
        "generate_audio": False,
        "camera_fixed": False,
    }

    status, result = post_task("/api/v3/contents/generations/tasks", body)

    if status not in (200, 201, 202):
        print(f"\n❌ T2V 提交失败 (HTTP {status})")
        save_result("t2v", body, result)
        return False

    task_id = result.get("id")
    print(f"\n✅ 任务提交成功: task_id={task_id}")

    poll_result = poll_task(task_id)
    success = poll_result is not None and poll_result.get("status") == "succeeded"
    save_result("t2v", body, result, poll_result)
    return success


def test_t2v_with_last_frame():
    """
    T2V + return_last_frame: 文生视频并返回尾帧
    """
    print_separator("T2V + return_last_frame: 文生视频+尾帧")

    body = {
        "model": MODEL_SD20,
        "content": [
            {"type": "text", "text": "一只小狗在沙滩上追逐海浪，欢快地奔跑"}
        ],
        "ratio": "16:9",
        "duration": 5,
        "resolution": "720p",
        "watermark": False,
        "generate_audio": False,
        "return_last_frame": True,
    }

    status, result = post_task("/api/v3/contents/generations/tasks", body)

    if status not in (200, 201, 202):
        print(f"\n❌ T2V+尾帧 提交失败 (HTTP {status})")
        save_result("t2v_last_frame", body, result)
        return False

    task_id = result.get("id")
    print(f"\n✅ 任务提交成功: task_id={task_id}")

    poll_result = poll_task(task_id)
    success = poll_result is not None and poll_result.get("status") == "succeeded"
    if success:
        content = poll_result.get("content", {})
        last_frame = content.get("last_frame_url", "")
        if last_frame:
            print(f"🖼️ 尾帧 URL: {last_frame[:120]}...")
        else:
            print("⚠️ return_last_frame=true 但未返回 last_frame_url")
    save_result("t2v_last_frame", body, result, poll_result)
    return success


def test_i2v_single():
    """
    I2V 单图: 图生视频-首帧（Seedance 2.0）
    官方 API: content 数组中 image_url 类型，无 role 字段（默认首帧）
    """
    print_separator("I2V 单图: 图生视频-首帧（Seedance 2.0）")

    body = {
        "model": MODEL_SD20,
        "content": [
            {"type": "text", "text": "猫咪转头看镜头，缓慢眨眼"},
            {"type": "image_url", "image_url": {"url": TEST_IMAGE_1}},
        ],
        "ratio": "adaptive",
        "duration": 5,
        "resolution": "720p",
        "watermark": False,
        "generate_audio": False,
    }

    status, result = post_task("/api/v3/contents/generations/tasks", body)

    if status not in (200, 201, 202):
        print(f"\n❌ I2V 单图 提交失败 (HTTP {status})")
        save_result("i2v_single", body, result)
        return False

    task_id = result.get("id")
    print(f"\n✅ 任务提交成功: task_id={task_id}")

    poll_result = poll_task(task_id)
    success = poll_result is not None and poll_result.get("status") == "succeeded"
    save_result("i2v_single", body, result, poll_result)
    return success


def test_i2v_multi():
    """
    I2V 多图: 图生视频-多模态参考（Seedance 2.0）
    官方 API: 第一张图无 role（首帧），其余 role="reference_image"
    """
    print_separator("I2V 多图: 多模态参考（Seedance 2.0）")

    body = {
        "model": MODEL_SD20,
        "content": [
            {"type": "text", "text": "图片中的猫咪转头看镜头，然后慢慢走向画面右侧"},
            {"type": "image_url", "image_url": {"url": TEST_IMAGE_1}, "role": "reference_image"},
            {"type": "image_url", "image_url": {"url": TEST_IMAGE_2}, "role": "reference_image"},
        ],
        "ratio": "16:9",
        "duration": 5,
        "resolution": "720p",
        "watermark": False,
        "generate_audio": False,
    }

    status, result = post_task("/api/v3/contents/generations/tasks", body)

    if status not in (200, 201, 202):
        print(f"\n❌ I2V 多图 提交失败 (HTTP {status})")
        save_result("i2v_multi", body, result)
        return False

    task_id = result.get("id")
    print(f"\n✅ 任务提交成功: task_id={task_id}")

    poll_result = poll_task(task_id)
    success = poll_result is not None and poll_result.get("status") == "succeeded"
    save_result("i2v_multi", body, result, poll_result)
    return success


def test_flf():
    """
    FLF: 首尾帧视频（Seedance 2.0）
    官方 API: role="first_frame" + role="last_frame"
    """
    print_separator("FLF: 首尾帧视频（Seedance 2.0）")

    body = {
        "model": MODEL_SD20,
        "content": [
            {"type": "text", "text": "从第一张图缓慢过渡到第二张图，平滑变换"},
            {"type": "image_url", "image_url": {"url": TEST_IMAGE_1}, "role": "first_frame"},
            {"type": "image_url", "image_url": {"url": TEST_IMAGE_2}, "role": "last_frame"},
        ],
        "ratio": "adaptive",
        "duration": 5,
        "resolution": "720p",
        "watermark": False,
        "generate_audio": False,
    }

    status, result = post_task("/api/v3/contents/generations/tasks", body)

    if status not in (200, 201, 202):
        print(f"\n❌ FLF 提交失败 (HTTP {status})")
        save_result("flf", body, result)
        return False

    task_id = result.get("id")
    print(f"\n✅ 任务提交成功: task_id={task_id}")

    poll_result = poll_task(task_id)
    success = poll_result is not None and poll_result.get("status") == "succeeded"
    save_result("flf", body, result, poll_result)
    return success


def test_rtv():
    """
    RTV: 多模态参考生成（Seedance 2.0 专属）
    官方 API: reference_image + reference_video + reference_audio
    这里只测图片参考（视频和音频需要额外素材）。
    """
    print_separator("RTV: 多模态参考生成（Seedance 2.0）")

    body = {
        "model": MODEL_SD20,
        "content": [
            {"type": "text", "text": "根据参考图片生成一段视频，猫咪在花园中漫步"},
            {"type": "image_url", "image_url": {"url": TEST_IMAGE_1}, "role": "reference_image"},
            {"type": "image_url", "image_url": {"url": TEST_IMAGE_2}, "role": "reference_image"},
        ],
        "ratio": "16:9",
        "duration": 5,
        "resolution": "720p",
        "watermark": False,
        "generate_audio": False,
    }

    status, result = post_task("/api/v3/contents/generations/tasks", body)

    if status not in (200, 201, 202):
        print(f"\n❌ RTV 提交失败 (HTTP {status})")
        save_result("rtv", body, result)
        return False

    task_id = result.get("id")
    print(f"\n✅ 任务提交成功: task_id={task_id}")

    poll_result = poll_task(task_id)
    success = poll_result is not None and poll_result.get("status") == "succeeded"
    save_result("rtv", body, result, poll_result)
    return success


def test_video_edit():
    """
    VIDEO_EDIT: 视频编辑（Seedance 2.0 专属）
    官方 API: reference_image + reference_video + 编辑提示词
    需要一个视频 URL，这里用 T2V 生成的视频（如果有）。
    """
    print_separator("VIDEO_EDIT: 视频编辑（Seedance 2.0）")

    # 尝试加载之前 T2V 生成的视频 URL
    t2v_result_path = os.path.join(RESULTS_DIR, "t2v.json")
    video_url = None
    if os.path.exists(t2v_result_path):
        with open(t2v_result_path, "r") as f:
            t2v_data = json.load(f)
            poll = t2v_data.get("poll_result", {})
            content = poll.get("content", {})
            if isinstance(content, dict):
                video_url = content.get("video_url", "")

    if not video_url:
        print("⚠️ 没有可用的视频 URL（需要先跑通 T2V），跳过 VIDEO_EDIT")
        print("  建议先跑 T2V 测试生成视频，再跑 VIDEO_EDIT")
        return None

    body = {
        "model": MODEL_SD20,
        "content": [
            {"type": "text", "text": "将视频中的画面色调调整为暖色调，增加阳光感"},
            {"type": "image_url", "image_url": {"url": TEST_IMAGE_1}, "role": "reference_image"},
            {"type": "video_url", "video_url": {"url": video_url}, "role": "reference_video"},
        ],
        "ratio": "16:9",
        "duration": 5,
        "resolution": "720p",
        "watermark": False,
        "generate_audio": False,
    }

    status, result = post_task("/api/v3/contents/generations/tasks", body)

    if status not in (200, 201, 202):
        print(f"\n❌ VIDEO_EDIT 提交失败 (HTTP {status})")
        save_result("video_edit", body, result)
        return False

    task_id = result.get("id")
    print(f"\n✅ 任务提交成功: task_id={task_id}")

    poll_result = poll_task(task_id)
    success = poll_result is not None and poll_result.get("status") == "succeeded"
    save_result("video_edit", body, result, poll_result)
    return success


def test_video_extend():
    """
    VIDEO_EXTEND: 视频延长（Seedance 2.0 专属）
    官方 API: reference_video（最多3个）+ text
    需要一个视频 URL，这里用 T2V 生成的视频（如果有）。
    """
    print_separator("VIDEO_EXTEND: 视频延长（Seedance 2.0）")

    # 尝试加载之前 T2V 生成的视频 URL
    t2v_result_path = os.path.join(RESULTS_DIR, "t2v.json")
    video_url = None
    if os.path.exists(t2v_result_path):
        with open(t2v_result_path, "r") as f:
            t2v_data = json.load(f)
            poll = t2v_data.get("poll_result", {})
            content = poll.get("content", {})
            if isinstance(content, dict):
                video_url = content.get("video_url", "")

    if not video_url:
        print("⚠️ 没有可用的视频 URL（需要先跑通 T2V），跳过 VIDEO_EXTEND")
        print("  建议先跑 T2V 测试生成视频，再跑 VIDEO_EXTEND")
        return None

    body = {
        "model": MODEL_SD20,
        "content": [
            {"type": "text", "text": "视频继续，猫咪跑向远方，画面逐渐拉远"},
            {"type": "video_url", "video_url": {"url": video_url}, "role": "reference_video"},
        ],
        "duration": 5,
        "watermark": False,
        "generate_audio": False,
    }

    status, result = post_task("/api/v3/contents/generations/tasks", body)

    if status not in (200, 201, 202):
        print(f"\n❌ VIDEO_EXTEND 提交失败 (HTTP {status})")
        save_result("video_extend", body, result)
        return False

    task_id = result.get("id")
    print(f"\n✅ 任务提交成功: task_id={task_id}")

    poll_result = poll_task(task_id)
    success = poll_result is not None and poll_result.get("status") == "succeeded"
    save_result("video_extend", body, result, poll_result)
    return success


def test_t2v_frames():
    """
    T14: T2V + frames 参数（仅 1.0 系列专用）
    官方 API: frames = 25 + 4n（29~289），优先于 duration
    用 Seedance 1.0 Pro 模型测试。
    """
    print_separator("T2V + frames 参数（Seedance 1.0 Pro）")

    body = {
        "model": MODEL_SD10_PRO,
        "content": [
            {"type": "text", "text": "一只小猫在院子里追蝴蝶，阳光明媚，温馨画面"}
        ],
        "ratio": "16:9",
        "frames": 33,  # 25 + 4*2 = 33（约 1.3 秒，25fps）
        "watermark": False,
        "generate_audio": False,
        "camera_fixed": False,
    }

    status, result = post_task("/api/v3/contents/generations/tasks", body)

    if status not in (200, 201, 202):
        print(f"\n❌ T2V+frames 提交失败 (HTTP {status})")
        save_result("t2v_frames", body, result)
        return False

    task_id = result.get("id")
    print(f"\n✅ 任务提交成功: task_id={task_id}")

    poll_result = poll_task(task_id)
    success = poll_result is not None and poll_result.get("status") == "succeeded"
    save_result("t2v_frames", body, result, poll_result)
    return success


def test_t2i_fast():
    """
    T15: T2I 提示词优化 fast 模式（仅 4.0 模型支持）
    官方 API: optimize_prompt_options: {"mode": "fast"}
    """
    print_separator("T2I 提示词优化 fast 模式（Seedream 4.0）")

    body = {
        "model": MODEL_SEEDREAM_40,
        "prompt": "一只穿着宇航服的猫在月球上漫步，背景是地球",
        "optimize_prompt_options": {"mode": "fast"},
        "size": "2K",
        "watermark": False,
    }

    status, result = post_sync("/api/v3/images/generations", body)

    success = status == 200
    if success:
        data = result.get("data", [])
        if isinstance(data, list):
            images = data
        elif isinstance(data, dict):
            images = data.get("images", [])
        else:
            images = []
        if images:
            for i, img in enumerate(images):
                url = img.get("url", "") if isinstance(img, dict) else str(img)
                print(f"🖼️ 图片 {i+1} URL: {url[:120]}...")
        print("\n✅ T2I fast 模式通过")
    else:
        print(f"\n❌ T2I fast 模式失败 (HTTP {status})")

    save_result("t2i_fast", body, result)
    return success


# ==================== 主流程 ====================

ALL_TESTS = {
    "t2i": ("文生图", test_t2i),
    "t2i_ref": ("文生图+参考图", test_t2i_with_reference),
    "t2i_seq": ("文生图+组图", test_t2i_sequential),
    "t2v": ("文生视频", test_t2v),
    "t2v_lf": ("文生视频+尾帧", test_t2v_with_last_frame),
    "i2v": ("图生视频-单图", test_i2v_single),
    "i2v_multi": ("图生视频-多图", test_i2v_multi),
    "flf": ("首尾帧视频", test_flf),
    "rtv": ("多模态参考", test_rtv),
    "edit": ("视频编辑", test_video_edit),
    "extend": ("视频延长", test_video_extend),
    "t2v_frames": ("文生视频+frames参数", test_t2v_frames),
    "t2i_fast": ("文生图+fast优化", test_t2i_fast),
}


def main():
    print("🧪 Seedance/Seedream 适配器 E2E 测试")
    print(f"时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"API Key: {API_KEY[:12]}...{API_KEY[-6:]}")
    print(f"BASE_URL: {BASE_URL}")
    print(f"结果保存: {RESULTS_DIR}")
    print()

    # 检查依赖
    try:
        import requests
    except ImportError:
        print("❌ 缺少 requests 库，请执行: pip3 install requests")
        sys.exit(1)

    # 确定要跑的测试
    if len(sys.argv) > 1:
        test_name = sys.argv[1]
        if test_name not in ALL_TESTS:
            print(f"❌ 未知测试: {test_name}")
            print(f"可用测试: {', '.join(ALL_TESTS.keys())}")
            sys.exit(1)
        tests_to_run = {test_name: ALL_TESTS[test_name]}
    else:
        tests_to_run = ALL_TESTS

    results = {}

    for name, (desc, func) in tests_to_run.items():
        try:
            result = func()
            results[name] = result
        except Exception as e:
            print(f"\n💥 测试异常: {name} - {e}")
            import traceback
            traceback.print_exc()
            result = False
            results[name] = False

        # 测试间隔
        if result is not None:
            print("\n⏳ 等待 3 秒后继续...")
            time.sleep(3)

    # 汇总
    print("\n" + "=" * 60)
    print("📊 E2E 测试结果汇总")
    print("=" * 60)
    passed = 0
    failed = 0
    skipped = 0
    for name, result in results.items():
        desc = ALL_TESTS[name][0]
        if result is True:
            print(f"  ✅ {name:12s} {desc}")
            passed += 1
        elif result is False:
            print(f"  ❌ {name:12s} {desc}")
            failed += 1
        else:
            print(f"  ⏭️ {name:12s} {desc}（跳过）")
            skipped += 1
    print(f"\n  通过: {passed}  失败: {failed}  跳过: {skipped}")
    print("=" * 60)


if __name__ == "__main__":
    main()