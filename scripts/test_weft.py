"""Regression checks for the repository verification script."""

from pathlib import Path
import unittest
from unittest.mock import patch

import weft


class GeneratedPathTests(unittest.TestCase):
    def test_only_paths_inside_repo_generated_dirs_are_excluded(self) -> None:
        repo_root = Path("/workspace/build/project")

        with patch.object(weft, "ROOT", repo_root):
            self.assertFalse(weft.is_generated_path(repo_root / "README.md"))
            self.assertTrue(weft.is_generated_path(repo_root / "build" / "generated.md"))
            self.assertTrue(weft.is_generated_path(repo_root / ".gradle" / "cache.md"))
            self.assertTrue(weft.is_generated_path(repo_root / "checkouts" / "vendor" / "README.md"))
            self.assertTrue(
                weft.is_generated_path(repo_root / ".swiftpm-locks" / "default" / "swiftPMCheckout" / "artifacts" / "sdk" / "NOTICE.md")
            )
            self.assertFalse(weft.is_generated_path(Path("/external/build/README.md")))


if __name__ == "__main__":
    unittest.main()
