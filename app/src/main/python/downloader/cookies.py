"""
Cookie handling and Netscape cookie format verification
"""
import os
import re

class CookieManager:
    def __init__(self, storage_dir: str):
        self.storage_dir = storage_dir
        self.cookie_file = os.path.join(storage_dir, "hypex_cookies.txt")

    def validate_netscape_format(self, content: str) -> bool:
        """
        Validate whether the given string conforms to standard Netscape / Mozilla cookie format.
        Lines start with '#' (comments) or tab-separated 7 fields.
        """
        if not content or not content.strip():
            return False
        
        valid_lines = 0
        for line in content.splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) >= 7:
                valid_lines += 1
        
        return valid_lines > 0

    def save_cookies(self, content: str) -> bool:
        if not self.validate_netscape_format(content):
            return False
        
        os.makedirs(self.storage_dir, exist_ok=True)
        with open(self.cookie_file, "w", encoding="utf-8") as f:
            f.write(content)
        return True

    def get_cookie_file_path(self) -> str:
        if os.path.exists(self.cookie_file) and os.path.getsize(self.cookie_file) > 0:
            return self.cookie_file
        return None

    def has_cookies(self) -> bool:
        return self.get_cookie_file_path() is not None

    def clear_cookies(self) -> bool:
        if os.path.exists(self.cookie_file):
            os.remove(self.cookie_file)
            return True
        return False
