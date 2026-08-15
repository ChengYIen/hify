# -*- coding: utf-8 -*-
"""MCP tools list/debug pages UI acceptance."""
import os
import asyncio
import sys

from playwright.async_api import async_playwright

BASE = "http://localhost:5173"
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
SHOT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "shots", "mcp-tools")
os.makedirs(SHOT_DIR, exist_ok=True)

results = []


def check(name, ok, detail=""):
    tag = "PASS" if ok else "FAIL"
    results.append((name, ok))
    print(f"[{tag}] {name}" + (f"  - {detail}" if detail else ""), flush=True)


async def shot(page, name):
    await page.screenshot(path=os.path.join(SHOT_DIR, f"{name}.png"), full_page=True)


async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(
            executable_path=CHROME,
            headless=True,
            args=["--no-sandbox", "--disable-gpu"],
        )
        page = await browser.new_page(viewport={"width": 1440, "height": 900})
        page.set_default_timeout(20000)

        print("===== login =====", flush=True)
        await page.goto(BASE + "/login", wait_until="networkidle")
        await page.fill('input[placeholder="请输入用户名"]', "accept_test")
        await page.fill('input[placeholder="请输入密码"]', "accept123")
        await page.click("button.login-btn")
        await page.wait_for_url("**/provider", timeout=20000)
        check("登录成功", "/provider" in page.url)

        print("===== list page =====", flush=True)
        await page.goto(BASE + "/mcp-tools", wait_until="networkidle")
        await page.wait_for_selector("text=MCP工具服务", timeout=15000)
        await page.wait_for_selector(".mcp-table", timeout=15000)
        body = await page.locator("body").inner_text()
        check("列表页标题", "MCP工具服务" in body)
        check("列表展示真实数据", "退款服务" in body and "localhost:9001" in body)
        check("描述列展示", "财务-退款MCP" in body)
        check("Admin 用户显示", "Admin" in body)
        await shot(page, "01-list")

        print("===== search/filter =====", flush=True)
        search = page.locator('input[placeholder="搜索名称 / Endpoint"]')
        await search.fill("ORD-NOT-FOUND")
        await page.wait_for_timeout(500)
        rows_after_search = await page.locator(".mcp-table .el-table__body tr").count()
        check("搜索无结果过滤", rows_after_search == 0, f"rows={rows_after_search}")
        await search.fill("localhost:9001")
        await page.wait_for_timeout(500)
        body_after = await page.locator("body").inner_text()
        check("搜索按 Endpoint 命中", "退款服务" in body_after)
        await search.fill("")

        status_select = page.locator(".status-select")
        await status_select.click()
        await page.locator(".el-select-dropdown__item:has-text('停用')").first.click()
        await page.wait_for_timeout(500)
        disabled_rows = await page.locator(".mcp-table .el-table__body tr").count()
        check("状态筛选停用", disabled_rows == 0, f"rows={disabled_rows}")
        await status_select.click()
        await page.locator(".el-select-dropdown__item:has-text('全部')").first.click()
        await page.wait_for_timeout(500)
        await shot(page, "02-list-filtered")

        print("===== debug page =====", flush=True)
        await page.locator("button:has-text('调试')").first.click()
        await page.wait_for_selector(".debug-header", timeout=15000)
        await page.wait_for_selector(".status-connected, .status-unreachable", timeout=20000)
        check("调试页 URL", "/mcp-tools/2/debug" in page.url)
        debug_body = await page.locator("body").inner_text()
        check("调试页名称", "退款服务" in debug_body)
        check("调试页 Endpoint", "localhost:9001" in debug_body)
        check("连通状态标签", "已连接" in debug_body or "无法连接" in debug_body)
        await shot(page, "03-debug")

        print("===== tool select + manual input =====", flush=True)
        tool_select = page.locator(".tool-select")
        await tool_select.locator("input").click()
        await page.wait_for_selector(".el-select-dropdown__item", timeout=20000)
        await page.locator(".el-select-dropdown__item:has-text('check_refund_eligibility')").first.click()
        await page.wait_for_timeout(500)
        manual_input = page.locator('input[placeholder="可手动输入工具名，与下拉联动"]')
        manual_value = await manual_input.input_value()
        check("下拉选中联动输入框", manual_value == "check_refund_eligibility", manual_value)

        await manual_input.fill("get_refund_status")
        await page.wait_for_timeout(300)
        select_text = await tool_select.inner_text()
        check("手动输入联动下拉", "get_refund_status" in select_text, select_text.strip()[:80])

        print("===== JSON execute =====", flush=True)
        await tool_select.locator("input").click()
        await page.wait_for_selector(".el-select-dropdown__item", timeout=20000)
        await page.locator(".el-select-dropdown__item:has-text('check_refund_eligibility')").first.click()
        await page.wait_for_timeout(300)
        textarea = page.locator(".json-textarea")
        await textarea.fill('{"orderId":"ORD-001"}')
        await page.locator("button:has-text('执行调用')").click()
        await page.wait_for_selector(".result-text", timeout=20000)
        result_text = await page.locator(".result-text").inner_text()
        check("调用结果返回 JSON", "eligible" in result_text, result_text[:120])
        await shot(page, "04-debug-result")

        print("===== refresh/back =====", flush=True)
        await page.locator("button:has-text('刷新工具')").click()
        await page.wait_for_timeout(1500)
        refreshed_body = await page.locator("body").inner_text()
        check("刷新后仍展示工具", "check_refund_eligibility" in refreshed_body)

        await page.locator("button:has-text('返回')").first.click()
        await page.wait_for_selector(".mcp-table", timeout=15000)
        check("返回列表页", "/mcp-tools" in page.url)
        await shot(page, "05-back-list")

        await browser.close()

    passed = sum(1 for _, ok in results if ok)
    print(f"PASS {passed} / {len(results)}", flush=True)
    failed = [name for name, ok in results if not ok]
    if failed:
        print("FAILED: " + ", ".join(failed), flush=True)
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
