"""参考转写输入校验的确定性测试。"""

from pathlib import Path
import tempfile
import unittest

from echofold_benchmark.errors import ManifestError
from echofold_benchmark.manifest import _read_reference_rows


class ReferenceInputTests(unittest.TestCase):
    def test_directory_with_supported_suffix_is_rejected_as_not_a_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            reference_directory = Path(directory) / "reference.json"
            reference_directory.mkdir()

            with self.assertRaisesRegex(ManifestError, "参考转写路径不是文件"):
                _read_reference_rows(reference_directory)


if __name__ == "__main__":
    unittest.main()
