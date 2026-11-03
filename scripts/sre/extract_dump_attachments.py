#!/usr/bin/env python3
"""
FlowDesk SRE Utility: Extract Attachment References from Database Dump
=====================================================================
Reads a PostgreSQL dump file (gzipped or plain text) and extracts all
active attachment records (storage_key and checksum_sha256) directly
from the captured snapshot, without connecting to any live database.

Outputs: One line per active attachment: "<storage_key>|<checksum_sha256>"
"""

import gzip
import re
import sys


def open_dump(path):
    if path.endswith('.gz'):
        return gzip.open(path, 'rt', encoding='utf-8', errors='replace')
    return open(path, 'r', encoding='utf-8', errors='replace')


def extract_attachments(dump_path):
    in_copy = False
    k_idx = -1
    s_idx = -1
    stat_idx = -1
    attachments = []

    with open_dump(dump_path) as f:
        for line in f:
            if not in_copy:
                # Detect COPY command for ticket_attachments table
                if 'COPY ' in line and 'ticket_attachments' in line and 'FROM stdin' in line:
                    in_copy = True
                    cols_match = re.search(r'\((.*?)\)', line)
                    if cols_match:
                        cols = [c.strip().strip('"') for c in cols_match.group(1).split(',')]
                        if 'storage_key' in cols:
                            k_idx = cols.index('storage_key')
                        if 'checksum_sha256' in cols:
                            s_idx = cols.index('checksum_sha256')
                        if 'status' in cols:
                            stat_idx = cols.index('status')
                # Also handle INSERT INTO ticket_attachments statements if --inserts was used
                elif line.startswith('INSERT INTO') and 'ticket_attachments' in line:
                    # Generic fallback: match UUID.dat and 64-char sha hex
                    key_match = re.search(r'([0-9a-fA-F-]{36}\.dat)', line)
                    sha_match = re.search(r'([0-9a-fA-F]{64})', line)
                    if key_match and sha_match and 'DELETED' not in line:
                        attachments.append((key_match.group(1), sha_match.group(1).lower()))
            else:
                # End of COPY block is marked by \. on its own line
                if line.strip() == r'\.':
                    in_copy = False
                    continue
                parts = line.rstrip('\r\n').split('\t')
                if k_idx >= 0 and s_idx >= 0 and len(parts) > max(k_idx, s_idx):
                    storage_key = parts[k_idx].strip()
                    checksum_sha256 = parts[s_idx].strip().lower()
                    status = parts[stat_idx].strip() if stat_idx >= 0 and len(parts) > stat_idx else 'ACTIVE'
                    if status != 'DELETED' and storage_key and checksum_sha256:
                        attachments.append((storage_key, checksum_sha256))

    return attachments


def main():
    if len(sys.argv) < 2:
        sys.stderr.write(f"Usage: {sys.argv[0]} <database.sql.gz>\n")
        sys.exit(1)

    dump_path = sys.argv[1]
    try:
        attachments = extract_attachments(dump_path)
        for storage_key, sha in attachments:
            print(f"{storage_key}|{sha}")
    except Exception as e:
        sys.stderr.write(f"Error parsing database dump {dump_path}: {e}\n")
        sys.exit(1)


if __name__ == '__main__':
    main()
