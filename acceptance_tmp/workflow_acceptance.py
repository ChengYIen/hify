# -*- coding: utf-8 -*-
"""Workflow list/create frontend acceptance script."""
import asyncio
import json
import os
import sys
import time

from playwright.async_api import async_playwright

BASE = "http://127.0.0.1:5173"
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
SHOT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "shots")
os.makedirs(SHOT_DIR, exist_ok=True)

results = []


def check(name, ok, detail=""):
    tag = "PASS" if ok else "FAIL"
    results.append((name, ok))
    print(f"[{tag}] {name}" + (f"  - {detail}" if detail else ""), flush=True)
    return ok


async def shot(page, name):
    await page.screenshot(path=os.path.join(SHOT_DIR, f"{name}.png"))


async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(
            executable_path=CHROME,
            headless=True,
            args=["--no-sandbox", "--disable-gpu"],
        )
        ctx = await browser.new_context(viewport={"width": 1440, "height": 900})
        page = await ctx.new_page()
        page.set_default_timeout(15000)

        print("===== 0. login =====", flush=True)
        await page.goto(BASE + "/login", wait_until="networkidle")
        await page.fill('input[placeholder="请输入用户名"]', "accept_test")
        await page.fill('input[placeholder="请输入密码"]', "accept123")
        await page.click("button.login-btn")
        await page.wait_for_url("**/provider", timeout=20000)
        check("登录成功", "/provider" in page.url)

        print("===== 1. workflow list =====", flush=True)
        await page.goto(BASE + "/workflows", wait_until="networkidle")
        await page.wait_for_selector(".hify-table__inner", timeout=15000)
        await page.wait_for_timeout(800)
        rows = await page.locator(".el-table__body tr").all_text_contents()
        row_text = "\n".join(rows)
        existing_names = ["wf_verify_0814", "智能客服分类工作流", "Customer refund workflow v2"]
        seen = [name for name in existing_names if name in row_text]
        check("列表显示之前创建的工作流", len(seen) > 0, f"seen={seen}")
        await shot(page, "01-workflow-list")

        print("===== 2. create page prefilled JSON =====", flush=True)
        await page.click("button:has-text('新建工作流')")
        await page.wait_for_url("**/workflows/create", timeout=10000)
        textarea = page.locator(".config-editor__textarea textarea")
        await textarea.wait_for(state="visible", timeout=10000)
        config = await textarea.input_value()
        prefilled = '"nodes"' in config and '"edges"' in config and '"prompt"' in config
        check("新建页预填示例 JSON", prefilled, f"len={len(config)}")

        print("===== 3. modify prompt + format =====", flush=True)
        parsed = json.loads(config)
        for node in parsed.get("nodes", []):
            node_config = node.get("config")
            if isinstance(node_config, dict) and "prompt" in node_config:
                node_config["prompt"] = "验收：判断问题类型，返回：售前/售后/技术支持"
                break
        compact = json.dumps(parsed, ensure_ascii=False, separators=(",", ":"))
        await textarea.fill(compact)
        lines_before = (await textarea.input_value()).count("\n")
        await page.click("button:has-text('格式化')")
        await page.wait_for_selector(".el-message--success", timeout=5000)
        formatted = await textarea.input_value()
        lines_after = formatted.count("\n")
        try:
            reparsed = json.loads(formatted)
            format_ok = reparsed == parsed
        except Exception:
            format_ok = False
        pretty_ok = (
            lines_before == 0
            and lines_after >= 10
            and '\n  "nodes"' in formatted
            and format_ok
        )
        check(
            "修改 Prompt 后格式化正常",
            pretty_ok,
            f"lines {lines_before} -> {lines_after}, parse_equal={format_ok}",
        )
        await shot(page, "02-format-json")

        print("===== 4. submit workflow =====", flush=True)
        unique = "acceptance_workflow_" + str(int(time.time() * 1000))
        await page.fill('input[placeholder="请输入工作流名称"]', unique)
        await page.fill(
            'textarea[placeholder="请输入工作流描述（可选）"]',
            "前端验收创建",
        )
        await page.click("button:has-text('提交')")
        await page.wait_for_url("**/workflows", timeout=15000)
        check("提交成功后跳回列表页", page.url.rstrip("/").endswith("/workflows"))
        await page.wait_for_selector(
            f".el-table__body tr:has-text('{unique}')",
            timeout=10000,
        )
        check("列表出现新建工作流", True, f"name={unique}")
        await shot(page, "03-workflow-created")

        print("===== 5. invalid JSON blocked =====", flush=True)
        await page.goto(BASE + "/workflows/create", wait_until="networkidle")
        await page.wait_for_selector(".config-editor__textarea textarea", timeout=10000)
        invalid_name = "invalid_json_" + str(int(time.time() * 1000))
        await page.fill('input[placeholder="请输入工作流名称"]', invalid_name)
        invalid_textarea = page.locator(".config-editor__textarea textarea")
        await invalid_textarea.fill('{ "nodes": [ }')
        posts = []
        page.on(
            "request",
            lambda req: posts.append(req.url)
            if req.method == "POST" and "/api/v1/workflows" in req.url
            else None,
        )
        await page.click("button:has-text('提交')")
        await page.wait_for_timeout(1500)
        error_msgs = await page.locator(".el-message--error").all_text_contents()
        error_ok = any("不是合法 JSON" in msg for msg in error_msgs)
        check("非法 JSON 前端拦截并提示", error_ok, f"msgs={error_msgs}")
        check("非法 JSON 未提交到后端", len(posts) == 0, f"posts={posts}")
        check("非法 JSON 未跳转页面", "/workflows/create" in page.url, f"url={page.url}")
        await shot(page, "04-invalid-json")

        print("===== summary =====", flush=True)
        passed = sum(1 for _, ok in results if ok)
        print(f"PASS {passed} / {len(results)}", flush=True)
        failed = [name for name, ok in results if not ok]
        if failed:
            print("FAILED: " + ", ".join(failed), flush=True)
            await browser.close()
            sys.exit(1)
        await browser.close()


if __name__ == "__main__":
    asyncio.run(main())
