import math, os, random, struct, sys, zlib

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
# The model maps uv [1,1,15,15], so each face is drawn on a 28x28 grid placed at pixel offset 2;
# the 2px border around it is plain wood (only visible in break particles).
SIZE, OFF, N = 32, 2, 28

# Carved wooden owl: spruce-brown planks, a lighter oak-toned facial disc, carved grooves with a lit lip.
WOOD = [hexc('7A5A34'), hexc('735532'), hexc('7E5E37')]
WOOD_GRAIN, WOOD_GAP = hexc('664A2B'), hexc('4F3820')
DISC = [hexc('B08A55'), hexc('A88350'), hexc('B48E59')]
DISC_GRAIN = hexc('9C784A')
CARVE = hexc('4A3420')
CARVE_HI = hexc('94744A')
FEATHER_LIGHT = hexc('8A6940')
GOLD = (1, 2, 3, 255)        # marker colour while drawing; metallize() replaces it with the shades below

# Metallic gold in the vanilla gold-block style: a base tone with lighter sheen and occasional
# bright glints, plus a slightly deeper tone. No dark/near-black pixels, and no one-sided bevel.
G_DEEP = hexc('C99A22')
G_BASE = hexc('E8B830')
G_LIGHT = hexc('F6D250')
G_GLINT = hexc('FFF2A6')
# shimmer pattern walked along thin lines
LINE_PATTERN = [G_BASE, G_BASE, G_LIGHT, G_GLINT, G_LIGHT, G_BASE, G_DEEP, G_BASE, G_LIGHT, G_BASE, G_DEEP, G_DEEP]

def metallize(px):
    gold = {(x, y) for y in range(N) for x in range(N) if px[y][x] == GOLD}
    for x, y in gold:
        around = sum((x + dx, y + dy) in gold for dx in (-1, 0, 1) for dy in (-1, 0, 1)) - 1
        if around == 8:
            # inside a filled area (beak, emblem): diagonal sheen bands like polished metal
            c = {0: G_GLINT, 1: G_LIGHT, 9: G_LIGHT, 5: G_DEEP, 6: G_DEEP}.get((x - y) % 10, G_BASE)
        else:
            # thin lines and the rims of filled areas: shimmer along the line
            c = LINE_PATTERN[(x + y) % len(LINE_PATTERN)]
        px[y][x] = c
    return px

def planks(seed, gaps=(18,)):
    """28x28 wood with horizontal board gaps and soft grain streaks"""
    rnd = random.Random(seed)
    px = [[rnd.choices(WOOD, weights=[6, 2, 2])[0] for _ in range(N)] for _ in range(N)]
    for y in range(N):
        if y in gaps:
            px[y] = [WOOD_GAP] * N
            continue
        x = rnd.randint(0, 6)
        while x < N:
            length = rnd.randint(3, 6)
            if rnd.random() < 0.45:
                for i in range(x, min(N, x + length)):
                    px[y][i] = WOOD_GRAIN
            x += length + rnd.randint(3, 8)
    return px

def put(px, x, y, c=GOLD):
    if 0 <= x < N and 0 <= y < N:
        px[y][x] = c

def frame(px):
    for i in range(N):
        for x, y in ((i, 0), (i, N - 1), (0, i), (N - 1, i)):
            put(px, x, y)

def corner_brackets(px):
    # inner L bracket with a small curl pointing into the face
    for cx, cy, sx, sy in ((0, 0, 1, 1), (N - 1, 0, -1, 1), (0, N - 1, 1, -1), (N - 1, N - 1, -1, -1)):
        for i in range(2, 7):
            put(px, cx + i * sx, cy + 2 * sy)
            put(px, cx + 2 * sx, cy + i * sy)
        put(px, cx + 4 * sx, cy + 4 * sy)

def ring(px, cx, cy, r0, r1):
    for y in range(N):
        for x in range(N):
            if r0 <= math.hypot(x + 0.5 - cx, y + 0.5 - cy) < r1:
                put(px, x, y)

def groove(px, pts):
    """carve a line: dark groove with a lighter lip one pixel below, so it reads as cut into the wood"""
    cells = set()
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            cells.add((round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)))
    for x, y in cells:
        if (x, y + 1) not in cells and 0 < y + 1 < N - 1:
            put(px, x, y + 1, CARVE_HI)
    for x, y in cells:
        put(px, x, y, CARVE)

def disc_dist(x, y):
    # distance to the nearer eye centre (the eye elements cover local cols 6..11 and 16..21, rows 8..13)
    return min(math.hypot(x + 0.5 - cx, (y + 0.5 - 11) * 1.1) for cx in (9, 19))

def front():
    px = planks(50, gaps=())
    rnd = random.Random(51)
    # heart-shaped facial disc around both eyes, in lighter wood
    for y in range(21):
        for x in range(N):
            if disc_dist(x, y) < 7.2:
                px[y][x] = DISC_GRAIN if rnd.random() < 0.18 else rnd.choice(DISC)
    # feather chevrons carved into the breast
    for row in range(3):
        y = 21 + row * 2
        for cx in range(3 + (row % 2) * 3, N - 2, 6):
            groove(px, [(cx - 2, y), (cx, y + 1), (cx + 2, y)])
    # rim of the facial disc
    for y in range(21):
        for x in range(1, N - 1):
            if 7.2 <= disc_dist(x, y) < 8.1:
                px[y][x] = CARVE
    frame(px)
    # gold rings around the eyes, swept brow tufts and a beak that doubles as the latch
    ring(px, 9, 11, 3.6, 4.6)
    ring(px, 19, 11, 3.6, 4.6)
    for i in range(5):
        put(px, 8 - i, 5 - i // 2); put(px, 9 - i, 5 - i // 2)
        put(px, 19 + i, 5 - i // 2); put(px, 20 + i, 5 - i // 2)
    for y, (a, b) in enumerate(((13, 14), (12, 15), (12, 15), (13, 14), (13, 14)), start=14):
        for x in range(a, b + 1):
            put(px, x, y)
    for cx, sx in ((0, 1), (N - 1, -1)):  # small gold corner leaves
        put(px, cx + 2 * sx, N - 3); put(px, cx + 3 * sx, N - 3); put(px, cx + 2 * sx, N - 4)
    return metallize(px)

def side():
    px = planks(52)
    # folded wing: overlapping carved feathers with lighter tips
    for i, y in enumerate((5, 10, 15)):
        x0, x1 = 5 + i * 2, 22 - i * 2
        groove(px, [(x0, y), (13, y + 6 - i), (x1, y)])
        for x in range(x0 + 2, x1 - 1, 3):
            put(px, x, y + 1, FEATHER_LIGHT)
    groove(px, [(4, 23), (23, 23)])
    frame(px)
    for x in (5, 22):  # gold rivets
        put(px, x, 4); put(px, x, 24)
    return metallize(px)

def top():
    px = planks(53, gaps=(7, 20))
    # carved scalloped feather rows across the lid, gold diamond centre
    for row, y in enumerate(range(5, N - 4, 6)):
        for cx in range(5 + (row % 2) * 3, N - 4, 6):
            groove(px, [(cx - 2, y), (cx, y + 2), (cx + 2, y)])
    frame(px)
    corner_brackets(px)
    c = (N - 1) / 2
    for y in range(N):
        for x in range(N):
            if abs(x - c) + abs(y - c) < 4.5:
                put(px, x, y)
    return metallize(px)

def pad(face, seed):
    """place the 28x28 face into a 32x32 texture with a plain wood border"""
    rnd = random.Random(seed)
    px = [[rnd.choices(WOOD, weights=[6, 2, 2])[0] for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(N):
        px[y + OFF][OFF:OFF + N] = face[y]
    return px

png(os.path.join(out, 'hot_deposit_side.png'), pad(side(), 1))
png(os.path.join(out, 'hot_deposit_front.png'), pad(front(), 2))
png(os.path.join(out, 'hot_deposit_top.png'), pad(top(), 3))

# Purple eyes, 16x16, mapped onto each 3x3 eye element.
# Open: glowing purple iris with a light highlight and a dark slit pupil.
# Closed: a wooden eyelid with a thin purple lash line, so the owl looks asleep.
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

LID = [hexc('9C784A'), hexc('A27E4E'), hexc('967346')]
LID_CREASE = hexc('7A5A34'); LASH = hexc('5B2A8C')
rnd = random.Random(7)
lid = [[rnd.choice(LID) for _ in range(16)] for _ in range(16)]
for x in range(16):
    lid[3][x] = LID_CREASE
    for y in (8, 9):
        lid[y][x] = LASH
    if x % 4 == 1:
        lid[10][x] = LASH
png(os.path.join(out, 'hot_deposit_eye_closed.png'), lid)
print('textures written')
