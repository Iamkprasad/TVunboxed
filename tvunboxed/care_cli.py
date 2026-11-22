"""CLI for TV Care: python3 -m tvunboxed care --tv IP --audit"""

import argparse
import json
import sys

from . import protocol, tvcare
from .client import Session
from .tvdav import WebDavClient


def main(argv=None):
    parser = argparse.ArgumentParser(prog="tvunboxed care", description=__doc__)
    parser.add_argument("--tv", required=True, help="TV IP address")
    parser.add_argument("--port", type=int, default=protocol.CONTROL_PORT_DEFAULT)
    parser.add_argument("--webdav-port", type=int, default=8000)
    parser.add_argument("--audit", action="store_true", help="print the full read-only report")
    parser.add_argument("--dry-clean", action="store_true",
                        help="list reclaimable files and token; deletes nothing")
    parser.add_argument("--stage", metavar="LOCAL_APK",
                        help="upload an APK to /sdcard/Upload and open the TV installer")
    parser.add_argument("--yes", help="required literal 'stage' when using --stage")
    parser.add_argument("--json", action="store_true", help="emit raw JSON for --audit")
    args = parser.parse_args(argv)

    dav = WebDavClient(args.tv, args.webdav_port, "/")
    session = Session(args.tv, args.port)

    if args.audit:
        report = tvcare.audit(session, dav, args.webdav_port)
        if args.json:
            print(json.dumps(report, indent=2, ensure_ascii=False))
            return 0
        print("TV CARE AUDIT - {}".format(args.tv))
        print("apps: {} system, {} user, {} unknown".format(
            report["app_counts"]["system"], report["app_counts"]["user"],
            report["app_counts"]["unknown"]))
        print("user files scanned: {}".format(report["storage"]["file_count"]))
        reclaim = report["reclaimable"]
        print("reclaimable candidates: {} files, {:.2f} MB".format(
            len(reclaim["items"]), reclaim["bytes"] / 1048576.0))
        for item in reclaim["items"]:
            print("  {:9.2f} MB  {:14s} {}".format(
                item["size"] / 1048576.0, item["reason"], item["path"]))
        print("cleanup token: {}".format(report["token"]))
        print(report["note"])
        return 0

    if args.dry_clean:
        report = tvcare.audit(session, dav, args.webdav_port)
        reclaim = report["reclaimable"]
        print("DRY RUN - nothing will be deleted")
        for item in reclaim["items"]:
            print("  {:9.2f} MB  {:14s} {}".format(
                item["size"] / 1048576.0, item["reason"], item["path"]))
        print("total: {:.2f} MB".format(reclaim["bytes"] / 1048576.0))
        print("token for exact file set: {}".format(report["token"]))
        print("cleanup copies files to /sdcard/backups/care/; purge them separately to free space")
        return 0

    if args.stage:
        if args.yes != "stage":
            print("refusing: pass --yes stage to confirm staging an APK", file=sys.stderr)
            return 2
        try:
            result = tvcare.stage_apk(dav, args.stage)
            session.send(result["openfile"])
        except (ValueError, OSError) as exc:
            print("error: {}".format(exc), file=sys.stderr)
            return 1
        print("staged: {}".format(result["remote"]))
        print(result["instruction"])
        return 0

    parser.print_help()
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
