#!/usr/bin/env python3
"""Reads a VR Desktop frame log (frames-*.log.gz from the phone) and says where the stutter comes from.

Usage:
  scripts/analyze-frame-log.py frame-logs/frames-20261112-210000.log.gz [--all] [--csv seconds.csv]

The format is in docs/FRAME-LOG.md. Only the Python 3 standard library is needed. A file cut short (the app was
killed) is read up to where it ends.
"""
import argparse
import collections
import datetime
import gzip
import json
import re
import sys
import zlib

# The phone writes its own values in the log's header (H,timing); these are for logs from before it did.
WINDOW_US = 2_000_000  # the fastest frame of this long is each stage's baseline
STALL_S = 0.25  # no frame for this long is frozen video, not stutter


def read_lines(path):
    """All complete lines of a gzip file, also one that was cut short."""
    data = open(path, 'rb').read()
    if path.endswith('.gz'):
        d = zlib.decompressobj(16 + zlib.MAX_WBITS)
        try:
            text = d.decompress(data)
        except zlib.error:
            text = b''
        data = text
    lines = data.decode('utf-8', 'replace').split('\n')
    if lines and lines[-1] == '':
        lines.pop()
    elif lines:
        lines.pop()  # a half-written last line
    return lines


def pct(values, p):
    if not values:
        return float('nan')
    s = sorted(values)
    k = min(len(s) - 1, max(0, int(round(p / 100.0 * (len(s) - 1)))))
    return s[k]


def dist(values, scale=1.0):
    if not values:
        return '-'
    v = [x * scale for x in values]
    return 'p50 %6.1f  p95 %6.1f  p99 %6.1f  max %7.1f  (n=%d)' % (pct(v, 50), pct(v, 95), pct(v, 99), max(v), len(v))


class RollingMin:
    """Smallest value over the last WINDOW_US, for values added in time order."""

    def __init__(self):
        self.q = collections.deque()

    def add(self, t, v):
        while self.q and self.q[-1][1] >= v:
            self.q.pop()
        self.q.append((t, v))
        while self.q[0][0] < t - WINDOW_US:
            self.q.popleft()
        return self.q[0][1]


def to_int(s):
    return int(s) if s not in ('', None) else None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('log')
    ap.add_argument('--all', action='store_true', help='list every stutter, not just the first 40')
    ap.add_argument('--csv', help='write a per-second table here')
    args = ap.parse_args()

    lines = read_lines(args.log)
    header = collections.OrderedDict()
    frames, refreshes, stutters, events, pc, phone, summaries, lost = [], [], [], [], [], [], [], []
    totals = verdict = None
    for line in lines:
        if not line or line.startswith('#'):
            continue
        kind, _, rest = line.partition(',')
        if kind == 'H':
            k, _, v = rest.partition(',')
            header[k] = v
        elif kind == 'F':
            p = rest.split(',')
            if len(p) < 13:
                continue
            frames.append(dict(n=int(p[0]), idr=p[1] == 'I', bytes=int(p[2]), pc=int(p[3]), host=int(p[4]),
                               rx=int(p[5]), cmp=int(p[6]), sub=int(p[7]), inn=to_int(p[8]), out=to_int(p[9]),
                               fate=p[10], rel=to_int(p[11]), latch=to_int(p[12])))
        elif kind == 'R':
            p = rest.split(',')
            refreshes.append((int(p[0]), int(p[1]), p[2]))
        elif kind == 'K':
            p = rest.split(',')
            d = dict(t=int(p[0]), cause=p[1], repeats=int(p[2]), prev=p[3], cur=p[4], frame=p[5])
            for kv in p[6:]:
                k, _, v = kv.partition('=')
                d[k] = v
            stutters.append(d)
        elif kind == 'E':
            t, _, text = rest.partition(',')
            events.append((to_int(t), text))
        elif kind == 'P':
            p = rest.split(',', 4)
            if len(p) == 5:
                pc.append((int(p[0]), p[1], p[2], p[3], p[4]))
        elif kind == 'Y':
            t, _, text = rest.partition(',')
            phone.append((int(t), dict(kv.split('=', 1) for kv in text.split(',') if '=' in kv)))
        elif kind == 'S':
            t, _, text = rest.partition(',')
            summaries.append((int(t), text))
        elif kind == 'L':
            p = rest.split(',')
            lost.append((int(p[0]), int(p[1]), int(p[2])))
        elif kind == 'T':
            totals = rest.partition(',')[2]
        elif kind == 'V':
            verdict = rest.partition(',')[2]

    global WINDOW_US, STALL_S
    m = re.search(r'windowMs=(\d+) stallMs=(\d+)', header.get('timing', ''))
    if m:
        WINDOW_US = int(m.group(1)) * 1000
        STALL_S = int(m.group(2)) / 1000.0

    start_ms = None
    m = re.search(r'unixMs=(\d+)', header.get('start', ''))
    if m:
        start_ms = int(m.group(1))

    def clock(t_us):
        """A phone time as the wall clock, to line up with what the user saw and the PC's logs."""
        if t_us is None or start_ms is None:
            return '%9.3fs' % ((t_us or 0) / 1e6)
        return datetime.datetime.fromtimestamp((start_ms + t_us / 1000) / 1000).strftime('%H:%M:%S.%f')[:-3]

    out = sys.stdout
    print('== %s' % args.log)
    for k, v in header.items():
        print('%-9s %s' % (k, v))
    duration = (refreshes[-1][0] if refreshes else (frames[-1]['sub'] if frames else 0)) / 1e6
    print('length    %.0f s, %d frames, %d refreshes%s' % (duration, len(frames), len(refreshes),
                                                       '' if totals else ' (the log was cut short: no totals)'))
    if verdict:
        print('\n' + verdict)
    if totals:
        print(totals)

    # ---- The PC's host app: v1 display mode, v2 stats
    v2 = []
    modes = []
    for t, path, status, rtt, body in pc:
        if status != '200':
            continue
        try:
            j = json.loads(body)
        except ValueError:
            continue
        if path.endswith('/v2/stats'):
            v2.append((t, j))
        elif path.endswith('/v1/mode'):
            mode = '%sx%s@%s%s' % (j.get('width'), j.get('height'), j.get('refreshHz'), ' HDR' if j.get('hdr') else '')
            if not modes or modes[-1][1] != mode:
                modes.append((t, mode))

    # Sunshine's capture restarts (v2 "sunshine"): each distinct lastRestartUnixMs is one, at the start of its gap.
    # More restarts than distinct times means several fell within one second.
    restarts = []
    restart_count = None
    first_count = None
    for t, j in v2:
        sun = j.get('sunshine') or {}
        if 'captureRestarts' in sun:
            restart_count = sun['captureRestarts']
            if first_count is None:
                first_count = restart_count
        last = sun.get('lastRestartUnixMs')
        if last is not None and start_ms is not None and (not restarts or restarts[-1][0] != last):
            restarts.append((last, (last - start_ms) * 1000))
    # A restart from before this log started (the host app's count runs since it started)
    restarts = [r for r in restarts if r[1] >= 0]

    # ---- Frames Sunshine re-sent unchanged: no processing time once it has sent some (a static screen). Repeats
    # around them aren't visible. Phone builds from 12 Nov 16:46 on leave them out themselves; older logs blamed them
    # on the network, so they're taken out here.
    resent = set()
    host_seen = False
    for f in sorted(frames, key=lambda x: x['n']):
        if f['host'] > 0:
            host_seen = True
        elif host_seen and not f['idr']:
            resent.add(f['n'])
    idle = 0
    real = []
    for s in stutters:
        try:
            involved = int(s['frame']) in resent or int(s['prev']) in resent
        except ValueError:
            involved = False
        if involved and not s['cause'].endswith('(STALL)'):
            idle += s['repeats']
        else:
            real.append(s)
    stutters = real

    # ---- Stutters by cause
    by_cause = collections.Counter()
    for s in stutters:
        if not s['cause'].endswith('(STALL)'):
            by_cause[s['cause']] += s['repeats']
    total_repeats = sum(by_cause.values())
    print('\n== Stutter (refreshes that repeated a frame, shorter than %g s)' % STALL_S)
    if refreshes:
        print('%d repeated refreshes = %.2f%% of %d' % (total_repeats, 100.0 * total_repeats / len(refreshes),
                                                       len(refreshes)))
    if resent:
        print('(%d frames were Sunshine re-sending an unchanged picture; %d repeats around them are left out)' %
              (len(resent), idle))
    for cause, n in by_cause.most_common():
        print('  %-14s %6d  %5.1f%%' % (cause, n, 100.0 * n / max(total_repeats, 1)))

    print('\n== Frozen video (no new frame for %g s or more)' % STALL_S)
    stall_events = [(t, e) for t, e in events if e.startswith('STALL')]
    if not stall_events:
        print('none')
    matched = set()
    for t, e in stall_events:
        m = re.search(r'no frame for ([0-9.]+) s', e)
        gap_start = t - float(m.group(1)) * 1e6 if m else t
        hit = None
        for i, (unix_ms, rt) in enumerate(restarts):
            # The two wall clocks agree to within a few tens of ms; Sunshine logs the start of its gap
            if gap_start - 1_000_000 <= rt <= gap_start + 1_500_000:
                hit = i
                break
        note = ''
        if hit is not None:
            matched.add(hit)
            note = '  <- Sunshine restarted capture at %s' % clock(restarts[hit][1])
        elif restarts or restart_count is not None:
            note = '  (no Sunshine restart then)'
        print('  %s  %s%s' % (clock(t), e, note))
    if restart_count is not None:
        during = restart_count - (first_count or 0)
        print('  Sunshine restarted capture %d time(s) during this stream (host app count); %d of %d stall(s) line '
              'up with one' % (during, len(matched), len(stall_events)))
        for i, (unix_ms, rt) in enumerate(restarts):
            if i not in matched:
                print('  %s  Sunshine restart with no stall on the phone' % clock(rt))

    # ---- Per-stage delays, against the fastest frame of the last 2 s at each stage (cancels clock offsets)
    stage = collections.defaultdict(list)
    mins = collections.defaultdict(RollingMin)
    pc_intervals = []
    prev = None
    for f in sorted(frames, key=lambda x: x['sub']):
        t = f['sub']
        if f['host'] > 0:
            stage['PC capture+encode (Sunshine), ms'].append(f['host'] / 10.0)
        stage['transfer, first to last packet, ms'].append((f['cmp'] - f['rx']) / 1000.0)
        stage['first packet later than fastest, ms'].append(
            (f['rx'] - f['pc'] - mins['rxpc'].add(t, f['rx'] - f['pc'])) / 1000.0)
        stage['frame complete later than fastest, ms'].append(
            (f['cmp'] - f['pc'] - mins['cmppc'].add(t, f['cmp'] - f['pc'])) / 1000.0)
        stage['queued before the decoder, ms'].append(
            (f['sub'] - f['cmp'] - mins['q'].add(t, f['sub'] - f['cmp'])) / 1000.0)
        if f['inn'] is not None:
            stage['waiting for a decoder input buffer, ms'].append((f['inn'] - f['sub']) / 1000.0)
        if f['out'] is not None:
            stage['decode (in to out), ms'].append((f['out'] - (f['inn'] or f['sub'])) / 1000.0)
            stage['decoded later than fastest, ms'].append(
                (f['out'] - f['pc'] - mins['outpc'].add(f['out'], f['out'] - f['pc'])) / 1000.0)
        if f['rel'] is not None and f['out'] is not None and f['rel'] > 0:
            stage['held by the pacer, ms'].append((f['rel'] - f['out']) / 1000.0)
        if f['latch'] is not None and f['rel'] is not None and f['rel'] > 0:
            stage['sent to latched, ms'].append((f['latch'] - f['rel']) / 1000.0)
        if (prev is not None and f['n'] == prev['n'] + 1 and 0 < f['pc'] - prev['pc'] < 1_000_000
                and f['n'] not in resent and prev['n'] not in resent):
            pc_intervals.append((f['pc'] - prev['pc']) / 1000.0)
        prev = f
    print('\n== Per frame, by stage')
    for name, values in stage.items():
        print('  %-40s %s' % (name, dist(values)))

    fates = collections.Counter(f['fate'] for f in frames)
    names = {'S': 'shown', 'W': 'overwritten on the surface', 'D': 'dropped (newer frame due)',
             'O': 'dropped (pacer full)', 'X': 'flushed with the decoder', 'N': 'not tracked'}
    print('\n== What happened to the frames')
    for k, n in fates.most_common():
        print('  %-28s %d' % (names.get(k, k), n))
    lost_total = sum(b - a + 1 for _, a, b in lost)
    print('  %-28s %d (in %d gaps)' % ('lost on the network', lost_total, len(lost)))
    idr = sum(1 for f in frames if f['idr'])
    print('  %-28s %d' % ('IDR (key) frames', idr))

    print('\n== The PC\'s frame spacing (capture timestamps, consecutive frames)')
    if pc_intervals:
        buckets = [(0, 8), (8, 14), (14, 19.5), (19.5, 25), (25, 40), (40, 100), (100, 1000)]
        for lo, hi in buckets:
            n = sum(1 for x in pc_intervals if lo <= x < hi)
            print('  %5.1f-%-6.1f ms %7d  %5.1f%%%s' % (lo, hi, n, 100.0 * n / len(pc_intervals),
                                                     '  <- normal at 60 fps' if lo == 14 else ''))

    if refreshes:
        intervals = [(b[0] - a[0]) / 1000.0 for a, b in zip(refreshes, refreshes[1:])]
        draws = [r[1] / 1000.0 for r in refreshes]
        print('\n== The phone\'s renderer')
        print('  %-40s %s' % ('time between refreshes, ms', dist(intervals)))
        print('  %-40s %s' % ('draw time (CPU), ms', dist(draws)))
        typical = pct(intervals, 50)
        missed = sum(1 for x in intervals if x > typical * 1.5)
        print('  missed refreshes (> 1.5x typical): %d' % missed)

    # ---- The PC's own numbers (host app v2) and display-mode changes (v1)
    print('\n== The PC (VR Desktop Host app)')
    if not pc:
        print('  no answers: the host app wasn\'t running or isn\'t reachable on port 48100')
    for t, mode in modes:
        print('  %s  display %s' % (clock(t), mode))
    if v2:
        games = [j['game'] for _, j in v2 if j.get('game')]
        if games:
            fps = [g.get('presents') for g in games if g.get('presents') is not None]
            pmax = [g.get('presentMsMax') for g in games if g.get('presentMsMax') is not None]
            dmax = [g.get('displayMsMax') for g in games if g.get('displayMsMax') is not None]
            dropped = sum(g.get('dropped') or 0 for g in games)
            print('  game %s: presents/s %s' % (games[-1].get('exe'), dist(fps)))
            print('  %-40s %s' % ('longest present gap per second, ms', dist(pmax)))
            print('  %-40s %s' % ('longest display gap per second, ms', dist(dmax)))
            print('  presents never displayed: %d; present mode %s' % (dropped, games[-1].get('presentMode')))
        enc = [j['gpu'].get('encoderPercent') for _, j in v2 if j.get('gpu') and j['gpu'].get('encoderPercent') is not None]
        gpu = [j['gpu'].get('utilPercent') for _, j in v2 if j.get('gpu') and j['gpu'].get('utilPercent') is not None]
        print('  %-40s %s' % ('GPU busy, %', dist(gpu)))
        print('  %-40s %s' % ('GPU video encoder busy, %', dist(enc)))

    if phone:
        print('\n== The phone')
        thermal = [int(d['thermal']) for _, d in phone if 'thermal' in d]
        batt = [float(d['battC']) for _, d in phone if 'battC' in d]
        if thermal:
            print('  thermal status max %d (0 none, 1 light, 2 moderate, 3 severe)' % max(thermal))
        if batt:
            print('  battery %.1f -> %.1f C (max %.1f)' % (batt[0], batt[-1], max(batt)))
        cpus = [d['cpuMHz'] for _, d in phone if 'cpuMHz' in d]
        if cpus:
            print('  CPU MHz (cores 0/4/7) first %s, last %s' % (cpus[0], cpus[-1]))
        wifi = [d['wifi'] for _, d in phone if 'wifi' in d]
        if wifi:
            print('  Wi-Fi first %s, last %s' % (wifi[0], wifi[-1]))

    print('\n== Events')
    for t, e in events:
        if not e.startswith('STALL'):
            print('  %s  %s' % (clock(t), e))

    print('\n== Stutters%s (time, cause, repeats, late frame and its extra delay per stage in ms)' %
          ('' if args.all else ', the first 40'))
    for s in (stutters if args.all else stutters[:40]):
        print('  %s  %-18s x%-3d frame %-7s pcGap %-6s late %-6s host %-5s net %-5s xfer %-5s queue %-5s '
              'decode %-5s drawGap %-5s %s' % (clock(s['t']), s['cause'], s['repeats'], s['frame'], s.get('pcGap'),
                                                s.get('late'), s.get('host'), s.get('net'), s.get('xfer'),
                                                s.get('queue'), s.get('decode'), s.get('drawGap'),
                                                s.get('fate', '')))

    # ---- The worst seconds, with the PC's numbers for the same second
    per_second = collections.defaultdict(lambda: collections.Counter())
    for s in stutters:
        if not s['cause'].endswith('(STALL)'):
            per_second[s['t'] // 1_000_000][s['cause']] += s['repeats']
    # The game's numbers are for the window that ended lagMs before the answer (PresentMon is about a second late)
    pc_by_second = {}
    for t, j in v2:
        lag_us = int(((j.get('game') or {}).get('lagMs') or 0) * 1000)
        pc_by_second.setdefault((t - lag_us) // 1_000_000, {})['game'] = j.get('game')
        pc_by_second.setdefault(t // 1_000_000, {})['gpu'] = j.get('gpu')
    worst = sorted(per_second.items(), key=lambda kv: -sum(kv[1].values()))[:10]
    if worst:
        print('\n== The 10 worst seconds')
        for sec, causes in sorted(worst):
            g = (pc_by_second.get(sec) or {}).get('game') or {}
            pcinfo = (' | PC game: %s presents, max gap %s ms, max display gap %s ms' %
                      (g.get('presents'), g.get('presentMsMax'), g.get('displayMsMax'))) if g else ''
            print('  %s  %s%s' % (clock(sec * 1_000_000), ', '.join('%s %d' % kv for kv in causes.most_common()),
                                 pcinfo))

    if args.csv:
        with open(args.csv, 'w') as f:
            f.write('second,clock,repeats,%s,pc_presents,pc_present_max_ms,pc_display_max_ms,gpu_encoder_pct\n' %
                    ','.join(sorted({c for cs in per_second.values() for c in cs})))
            causes = sorted({c for cs in per_second.values() for c in cs})
            last = int(duration) + 1
            for sec in range(last):
                cs = per_second.get(sec, {})
                j = pc_by_second.get(sec) or {}
                g = j.get('game') or {}
                gpu = j.get('gpu') or {}
                f.write('%d,%s,%d,%s,%s,%s,%s,%s\n' % (sec, clock(sec * 1_000_000), sum(cs.values()),
                                                       ','.join(str(cs.get(c, 0)) for c in causes),
                                                       g.get('presents', ''), g.get('presentMsMax', ''),
                                                       g.get('displayMsMax', ''), gpu.get('encoderPercent', '')))
        print('\nper-second table: %s' % args.csv)


if __name__ == '__main__':
    main()
