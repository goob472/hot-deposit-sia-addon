import os, random, struct, sys, zlib

out = sys.argv[1]
os.makedirs(out, exist_ok=True)

def png(path, px):
    h = len(px); w = len(px[0])
    raw = b''.join(b'\x00' + b''.join(struct.pack('BBBB', *p) for p in row) for row in px)
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(data)

def hexc(s):
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)

# 32x32 textures (twice vanilla resolution) so the gold trim can be a thin, continuous line.
# The model maps uv [1,1,15,15], so each face is the 28x28 area at pixel offset 2.
SIZE, OFF, N = 32, 2, 28

# Plank-like teal body in the vanilla chest style: horizontal boards and soft grain streaks.
# Every shade stays close to the base teal so nothing reads as a dark speck.
TEAL = [hexc('1E4D4A'), hexc('205250'), hexc('1D4B48')]
TEAL_GRAIN = hexc('1A4542')
TEAL_GAP = hexc('173E3C')
GOLD = hexc('E8B830')        # marker colour while drawing; metallize() replaces it with the shades below

# Metallic gold in the vanilla gold-block style: a base tone with lighter sheen and occasional
# bright glints, plus a slightly deeper tone. No dark/near-black pixels, and no one-sided bevel.
G_DEEP = hexc('C99A22')
G_BASE = hexc('E8B830')
G_LIGHT = hexc('F6D250')
G_GLINT = hexc('FFF2A6')
# shimmer pattern walked along thin lines
LINE_PATTERN = [G_BASE, G_BASE, G_LIGHT, G_GLINT, G_LIGHT, G_BASE, G_DEEP, G_BASE, G_LIGHT, G_BASE, G_DEEP, G_DEEP]

def metallize(px):
    gold = {(x, y) for y in range(SIZE) for x in range(SIZE) if px[y][x] == GOLD}
    for x, y in gold:
        around = sum((x + dx, y + dy) in gold for dx in (-1, 0, 1) for dy in (-1, 0, 1)) - 1
        if around == 8:
            # inside a filled area (plate, emblem centre): diagonal sheen bands like polished metal
            band = (x - y) % 10
            c = {0: G_GLINT, 1: G_LIGHT, 9: G_LIGHT, 5: G_DEEP, 6: G_DEEP}.get(band, G_BASE)
        else:
            # thin lines and the rims of filled areas: shimmer along the line
            c = LINE_PATTERN[(x + y) % len(LINE_PATTERN)]
        px[y][x] = c
    return px

def face(seed, seam=True, eye_frames=False, emblem=False, gaps=(18,)):
    rnd = random.Random(seed)
    px = [[rnd.choices(TEAL, weights=[6, 2, 2])[0] for _ in range(SIZE)] for _ in range(SIZE)]

    def put(lx, ly, c):
        px[ly + OFF][lx + OFF] = c

    for ly in range(N):
        if ly in gaps:
            for lx in range(N):
                put(lx, ly, TEAL_GAP)
            continue
        # a few horizontal grain streaks per board row
        lx = rnd.randint(0, 6)
        while lx < N:
            length = rnd.randint(3, 6)
            if rnd.random() < 0.45:
                for i in range(lx, min(N, lx + length)):
                    put(i, ly, TEAL_GRAIN)
            lx += length + rnd.randint(3, 8)

    # thin continuous gold frame
    for i in range(N):
        for lx, ly in ((i, 0), (i, N - 1), (0, i), (N - 1, i)):
            put(lx, ly, GOLD)

    # ornate corners: an inner L bracket with a small curl pointing into the face
    for cx, cy, sx, sy in ((0, 0, 1, 1), (N - 1, 0, -1, 1), (0, N - 1, 1, -1), (N - 1, N - 1, -1, -1)):
        for i in range(2, 7):
            put(cx + i * sx, cy + 2 * sy, GOLD)
            put(cx + 2 * sx, cy + i * sy, GOLD)
        put(cx + 4 * sx, cy + 4 * sy, GOLD)
        put(cx + 5 * sx, cy + 5 * sy, GOLD)

    if seam:
        # lid seam: thin gold line with small diamond studs
        for lx in range(N):
            put(lx, 9, GOLD)
        for sx in (6, N - 7):
            put(sx, 8, GOLD)
            put(sx, 10, GOLD)

    if eye_frames:
        # gold frames around the two eyes, joined by a bridge, with a small diamond crest above
        # (the eye elements cover local cols 6..11 and 16..21, rows 8..13)
        for x0 in (5, 15):
            x1 = x0 + 7
            for ly in range(7, 15):
                for lx in range(x0, x1 + 1):
                    edge = ly in (7, 14) or lx in (x0, x1)
                    corner = ly in (7, 14) and lx in (x0, x1)
                    if edge and not corner:
                        put(lx, ly, GOLD)
        for lx in (13, 14):
            for ly in (10, 11):
                put(lx, ly, GOLD)
        for lx, ly in ((13, 3), (14, 3), (12, 4), (15, 4), (13, 4), (14, 4), (13, 5), (14, 5)):
            put(lx, ly, GOLD)

    if emblem:
        # gold diamond emblem with a solid centre in the middle of the lid
        c = (N - 1) / 2
        for ly in range(N):
            for lx in range(N):
                d = abs(lx - c) + abs(ly - c)
                if 6 <= d < 7 or d < 2.5:
                    put(lx, ly, GOLD)
    return metallize(px)

png(os.path.join(out, 'hot_deposit_side.png'), face(1))
png(os.path.join(out, 'hot_deposit_front.png'), face(2, eye_frames=True))
png(os.path.join(out, 'hot_deposit_top.png'), face(3, seam=False, emblem=True, gaps=(7, 20)))

# Purple eyes, 16x16, mapped onto each 3x3 eye element.
# Open: glowing purple iris with a light highlight and a dark slit pupil.
# Closed: a teal eyelid with a thin purple lash line, so the front still reads as two eyes.
EYE_RIM = hexc('3E1A63'); EYE_OUT = hexc('6A2FA0'); EYE_MID = hexc('8E45CF'); EYE = hexc('B36BFF')
EYE_HI = hexc('E6C8FF'); PUPIL = hexc('24093D'); PUPIL_HI = hexc('4A1C73')
eye = [[EYE_RIM] * 16 for _ in range(16)]
for y in range(16):
    for x in range(16):
        dx, dy = x - 7.5, y - 7.5
        d = (dx * dx + dy * dy) ** 0.5
        if d < 7.2:
            eye[y][x] = EYE_OUT
        if d < 5.6:
            eye[y][x] = EYE_MID
        if d < 4.4:
            eye[y][x] = EYE
        if 3.2 < d < 4.6 and dy < -0.5 and dx < 0.5:
            eye[y][x] = EYE_HI
        if abs(dx) < 1.0 and abs(dy) < 5.0:
            eye[y][x] = PUPIL
        if abs(dx) < 1.0 and abs(dy) < 5.0 and abs(dy) > 3.5:
            eye[y][x] = PUPIL_HI
png(os.path.join(out, 'hot_deposit_eye_open.png'), eye)

LID = [hexc('2A625E'), hexc('2D6763'), hexc('285D59')]
LID_CREASE = hexc('225450'); LASH = hexc('5B2A8C')
rnd = random.Random(7)
lid = [[rnd.choice(LID) for _ in range(16)] for _ in range(16)]
for x in range(16):
    lid[3][x] = LID_CREASE
    for y in (8, 9):
        lid[y][x] = LASH
    if x % 4 == 1:
        lid[10][x] = LASH
png(os.path.join(out, 'hot_deposit_eye_closed.png'), lid)

old = os.path.join(out, 'hot_deposit_eye.png')
if os.path.exists(old):
    os.remove(old)
print('textures written')
