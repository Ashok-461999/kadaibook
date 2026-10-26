from __future__ import annotations

import logging
import time
from pathlib import Path

logger = logging.getLogger(__name__)


class RunAssetStore:
    """Keeps large image bytes out of LangGraph checkpoints.

    State holds paths, not megabytes. Files are removed after an invocation
    completes normally; a crashed run leaves them in place so the checkpoint can
    resume. Stale assets are swept opportunistically.
    """

    def __init__(self, root: Path) -> None:
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)

    def original_path(self, run_id: str, suffix: str) -> Path:
        return self.root / f"{run_id}.original{suffix}"

    def processed_path(self, run_id: str) -> Path:
        return self.root / f"{run_id}.processed.png"

    def save(self, path: Path, data: bytes) -> Path:
        temp = path.with_suffix(path.suffix + ".tmp")
        temp.write_bytes(data)
        temp.replace(path)
        return path

    def cleanup(self, paths: list[Path]) -> None:
        for path in paths:
            try:
                path.unlink(missing_ok=True)
            except OSError:  # pragma: no cover - best effort cleanup
                logger.warning("could not remove run asset %s", path)

    def sweep(self, max_age_hours: int = 48) -> int:
        cutoff = time.time() - max_age_hours * 3600
        removed = 0
        for path in self.root.glob("*"):
            try:
                if path.is_file() and path.stat().st_mtime < cutoff:
                    path.unlink()
                    removed += 1
            except OSError:  # pragma: no cover
                continue
        return removed
