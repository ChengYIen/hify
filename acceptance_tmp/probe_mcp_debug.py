# -*- coding: utf-8 -*-
import asyncio
from playwright.async_api import async_playwright

CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"


async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(executable_path=CHROME, headless=True, args=["--no-sandbox"])
        page = await browser.new_page(viewport={"width": 1440, "height": 900})
        await page.goto("http://localhost:5173/login", wait_until="networkidle")
        await page.fill('input[placeholder="请输入用户名"]', "accept_test")
        await page.fill('input[placeholder="请输入密码"]', "accept123")
        await page.click("button.login-btn")
        await page.wait_for_url("**/provider", timeout=20000)
        await page.goto("http://localhost:5173/mcp-tools/2/debug?name=x", wait_until="networkidle")
        await page.wait_for_selector(".status-connected, .status-unreachable", timeout=30000)
        print("status text:", await page.locator(".status-tag").inner_text())
        tool_select = page.locator(".tool-select")
        print("input count in select:", await tool_select.locator("input").count())
        await tool_select.locator("input").click()
        await page.wait_for_timeout(1500)
        print("dropdown count:", await page.locator(".el-select-dropdown").count())
        print("item count:", await page.locator(".el-select-dropdown__item").count())
        print("tool-option count:", await page.locator(".tool-option").count())
        html = await page.locator(".tool-select").inner_html()
        print("select html head:", html[:600])
        body = await page.locator("body").inner_text()
        print("has check_refund_eligibility:", "check_refund_eligibility" in body)
        await browser.close()


asyncio.run(main())
