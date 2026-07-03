#!/usr/bin/env python3
"""
Seedance (火山方舟) 端到端验证测试
认证方式：API Key Bearer Token（方舟平台标准认证）
API 文档：https://www.volcengine.com/docs/82379/1520757
"""

import json
import time
from datetime import datetime

import requests

# ==================== 配置 ====================
API_KEY = "ark-7e05ed56-f109-434e-b3ef-0c2a276e4f56-3d070"
BASE_URL = "https://ark.cn-beijing.volces.com"

# 模型 ID（方舟平台用 doubao 前缀，不是 dreamina）
MODEL_SD20 = "doubao-seedance-2-0-260128"
MODEL_SD20_FAST = "doubao-seedance-2-0-fast-260128"
MODEL_SD15_PRO = "doubao-seedance-1-5-pro-251215"

# ==================== 通用请求 ====================

def auth_headers():
    return {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json",
    }

def post(path, body):
    url = BASE_URL + path
    resp = requests.post(url, headers=auth_headers(), json=body, timeout=30)
    return resp

def get(path):
    url = BASE_URL + path
    resp = requests.get(url, headers=auth_headers(), timeout=30)
    return resp

# ==================== 测试用例 ====================

def test_v0_auth():
    """V0: 验证 API Key 是否被方舟平台接受"""
    print("\n" + "=" * 60)
    print("V0: 火山方舟 API Key 认证验证")
    print("=" * 60)

    # 用最小请求测试认证
    body = {
        "model": MODEL_SD20,
        "content": [
            {"type": "text", "text": "一只橘猫在草地上奔跑"}
        ],
    }

    print(f"POST {BASE_URL}/api/v3/contents/generations/tasks")
    print(f"Body: {json.dumps(body, ensure_ascii=False)[:100]}...")

    resp = post("/api/v3/contents/generations/tasks", body)
    print(f"\nHTTP Status: {resp.status_code}")

    try:
        result = resp.json()
        print(f"Response: {json.dumps(result, ensure_ascii=False, indent=2)[:800]}")
    except:
        print(f"Response: {resp.text[:500]}")

    if resp.status_code == 401:
        print("\n❌ V0 认证失败：401 Unauthorized")
        return False
    elif resp.status_code in (200, 201, 202):
        print("\n✅ V0 认证通过 + 任务提交成功")
        return result
    else:
        # 非401 说明认证通过，可能是参数问题
        print(f"\n⚠️ 状态码 {resp.status_code}，认证可能通过（参数可能有问题）")
        return resp.status_code != 401


def test_v1_t2v_sd20():
    """V1: Seedance 2.0 文生视频"""
    print("\n" + "=" * 60)
    print("V1: Seedance 2.0 文生视频")
    print("=" * 60)

    body = {
        "model": MODEL_SD20,
        "content": [
            {"type": "text", "text": "一只橘猫在草地上奔跑，阳光明媚，高清画质"}
        ],
        "generate_audio": False,
        "resolution": "720p",
        "ratio": "16:9",
        "duration": 5,
    }

    print(f"Body: {json.dumps(body, ensure_ascii=False)[:200]}...")

    resp = post("/api/v3/contents/generations/tasks", body)
    print(f"\nHTTP Status: {resp.status_code}")

    try:
        result = resp.json()
        print(f"Response: {json.dumps(result, ensure_ascii=False, indent=2)[:800]}")
    except:
        print(f"Response: {resp.text[:500]}")
        return None

    if resp.status_code in (200, 201, 202):
        task_id = result.get("id")
        print(f"\n✅ 任务提交成功: task_id={task_id}")
        return task_id
    else:
        print(f"\n❌ 任务提交失败")
        return None


def test_poll(task_id, max_wait=600, interval=9):
    """轮询任务状态"""
    print(f"\n轮询任务 {task_id}...")
    path = f"/api/v3/contents/generations/tasks/{task_id}"

    start = time.time()
    while time.time() - start < max_wait:
        try:
            resp = get(path)
            result = resp.json()
            status = result.get("status", "unknown")
            elapsed = int(time.time() - start)

            if status == "running":
                print(f"  [{elapsed}s] running...")
            elif status == "queued":
                print(f"  [{elapsed}s] queued...")
            else:
                print(f"  [{elapsed}s] status={status}")

            if status == "succeeded":
                print(f"\n✅ 任务完成！")
                # 提取视频 URL
                content = result.get("content", {})
                video_url = content.get("video_url", "") if isinstance(content, dict) else ""
                if video_url:
                    print(f"视频 URL: {video_url[:100]}...")
                print(f"Full Response:\n{json.dumps(result, ensure_ascii=False, indent=2)[:2000]}")
                return result
            elif status == "failed":
                error = result.get("error", {})
                print(f"\n❌ 任务失败: {error}")
                print(f"Full Response:\n{json.dumps(result, ensure_ascii=False, indent=2)[:1000]}")
                return result
            elif status == "expired":
                print(f"\n⏰ 任务超时")
                return result
        except Exception as e:
            print(f"  轮询异常: {e}")

        time.sleep(interval)

    print(f"\n⏰ 轮询超时 ({max_wait}s)")
    return None


def test_v_err():
    """V-ERR: 错误传播测试"""
    print("\n" + "=" * 60)
    print("V-ERR: 错误传播测试")
    print("=" * 60)

    # 空 content
    body = {
        "model": MODEL_SD20,
        "content": [],
    }

    resp = post("/api/v3/contents/generations/tasks", body)
    print(f"\n[空 content] HTTP Status: {resp.status_code}")
    try:
        print(f"Response: {json.dumps(resp.json(), ensure_ascii=False, indent=2)[:500]}")
    except:
        print(f"Response: {resp.text[:300]}")

    # 无效模型
    body2 = {
        "model": "invalid-model-name",
        "content": [{"type": "text", "text": "测试"}],
    }

    resp2 = post("/api/v3/contents/generations/tasks", body2)
    print(f"\n[无效模型] HTTP Status: {resp2.status_code}")
    try:
        print(f"Response: {json.dumps(resp2.json(), ensure_ascii=False, indent=2)[:500]}")
    except:
        print(f"Response: {resp2.text[:300]}")

    if resp.status_code in (400, 422) and resp2.status_code in (400, 404, 422):
        print("\n✅ 错误正确传播")
        return True
    else:
        print("\n⚠️ 错误传播格式需确认")
        return False


# ==================== 主流程 ====================

if __name__ == "__main__":
    import sys

    print("🧪 Seedance (火山方舟) 端到端验证测试")
    print(f"时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"API Key: {API_KEY[:12]}...{API_KEY[-6:]}")
    print(f"BASE_URL: {BASE_URL}")
    print(f"Model: {MODEL_SD20}")

    results = {}

    # V0: 认证验证
    v0_result = test_v0_auth()
    if v0_result is False:
        print("\n❌ 认证失败，跳过后续测试")
        sys.exit(1)

    # 如果 V0 直接返回了 task_id（认证+提交一次通过）
    if isinstance(v0_result, dict) and v0_result.get("id"):
        task_id = v0_result["id"]
        print(f"\nV0 直接拿到了 task_id: {task_id}，跳到轮询")
        results["v0_auth"] = True
        results["v1_t2v"] = task_id
    else:
        results["v0_auth"] = True

        # V-ERR: 错误传播
        results["v_err"] = test_v_err()

        # V1: 文生视频
        task_id = test_v1_t2v_sd20()
        results["v1_t2v"] = task_id

    # 轮询
    if task_id:
        poll_result = test_poll(task_id)
        results["v1_poll"] = poll_result is not None and (isinstance(poll_result, dict) and poll_result.get("status") == "succeeded")

    # 汇总
    print("\n" + "=" * 60)
    print("📊 验证结果汇总")
    print("=" * 60)
    for k, v in results.items():
        if isinstance(v, bool):
            status = "✅" if v else "❌"
            print(f"  {k}: {status}")
        elif isinstance(v, str):
            print(f"  {k}: task_id={v}")
        else:
            print(f"  {k}: {v}")