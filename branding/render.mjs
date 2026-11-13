/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Resvg } from '@resvg/resvg-js';

const brandingDirectory = dirname(fileURLToPath(import.meta.url));

function renderWidth(source, width) {
    return new Resvg(source, { fitTo: { mode: 'width', value: width } }).render().asPng();
}

// the glyph is scaled to glyphHeight and pressed into the top right corner of a square canvas, whole
function renderFileIcon(source, canvas, glyphHeight) {
    const bounds = new Resvg(source).getBBox();
    const scale = glyphHeight / bounds.height;
    const translateX = canvas - (bounds.x + bounds.width) * scale;
    const translateY = -bounds.y * scale;
    const inner = source.toString().replace(/^[\s\S]*?<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '');
    const wrapped = `<svg xmlns="http://www.w3.org/2000/svg" width="${canvas}" height="${canvas}" viewBox="0 0 ${canvas} ${canvas}"><g transform="translate(${translateX} ${translateY}) scale(${scale})">${inner}</g></svg>`;
    return new Resvg(wrapped, { fitTo: { mode: 'width', value: canvas } }).render().asPng();
}

function packIcon(images) {
    const header = Buffer.alloc(6 + 16 * images.length);
    header.writeUInt16LE(1, 2);
    header.writeUInt16LE(images.length, 4);
    let offset = header.length;
    images.forEach(({ size, png }, index) => {
        const entry = 6 + 16 * index;
        header.writeUInt8(size, entry);
        header.writeUInt8(size, entry + 1);
        header.writeUInt16LE(1, entry + 4);
        header.writeUInt16LE(32, entry + 6);
        header.writeUInt32LE(png.length, entry + 8);
        header.writeUInt32LE(offset, entry + 12);
        offset += png.length;
    });
    return Buffer.concat([header, ...images.map(({ png }) => png)]);
}

const [outputDirectory, ...specifications] = process.argv.slice(2);

for (const specification of specifications) {
    const [file, rest] = specification.split('=');
    const [stem, request] = rest.split('@');
    const source = readFileSync(join(brandingDirectory, `${stem}.svg`));
    let content;
    if (file.endsWith('.ico')) {
        content = packIcon(request.split(',').map((size) => ({ size: Number(size), png: renderWidth(source, Number(size)) })));
    } else if (request.includes('+')) {
        const [canvas, glyphHeight] = request.split('+').map(Number);
        content = renderFileIcon(source, canvas, glyphHeight);
    } else {
        content = renderWidth(source, Number(request));
    }
    const target = join(outputDirectory, file);
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, content);
}
