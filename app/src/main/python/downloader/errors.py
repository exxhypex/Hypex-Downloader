"""
Error classification and handling for HYPEX Downloader Engine
"""

class EngineError(Exception):
    def __init__(self, code: str, message: str, raw_error: Exception = None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.raw_error = raw_error

    def to_dict(self):
        return {
            "code": self.code,
            "message": self.message,
            "raw": str(self.raw_error) if self.raw_error else None
        }

class NetworkError(EngineError):
    def __init__(self, message: str, raw_error=None):
        super().__init__("NETWORK_ERROR", message, raw_error)

class VideoUnavailableError(EngineError):
    def __init__(self, message: str, raw_error=None):
        super().__init__("VIDEO_UNAVAILABLE", message, raw_error)

class AgeRestrictedError(EngineError):
    def __init__(self, message: str, raw_error=None):
        super().__init__("AGE_RESTRICTED", message, raw_error)

class GeoRestrictedError(EngineError):
    def __init__(self, message: str, raw_error=None):
        super().__init__("GEO_RESTRICTED", message, raw_error)

class FormatNotAvailableError(EngineError):
    def __init__(self, message: str, raw_error=None):
        super().__init__("FORMAT_NOT_AVAILABLE", message, raw_error)

class DownloadCancelledError(EngineError):
    def __init__(self, message="Download was cancelled by user"):
        super().__init__("DOWNLOAD_CANCELLED", message)

def classify_error(err: Exception) -> EngineError:
    err_str = str(err).lower()
    if "private video" in err_str or "this video is unavailable" in err_str or "deleted video" in err_str:
        return VideoUnavailableError("This media is private, deleted, or unavailable.", err)
    elif "sign in to confirm your age" in err_str or "age" in err_str and "restricted" in err_str:
        return AgeRestrictedError("This media is age-restricted and requires account cookies.", err)
    elif "not available in your country" in err_str or "geo" in err_str:
        return GeoRestrictedError("This media is geographically restricted.", err)
    elif "connection" in err_str or "timed out" in err_str or "network" in err_str or "dns" in err_str:
        return NetworkError("Network timeout or connection refused while fetching media.", err)
    elif "requested format not available" in err_str:
        return FormatNotAvailableError("Selected format or stream is not available.", err)
    else:
        return EngineError("EXTRACTION_ERROR", str(err), err)
