import { describe, expect, it } from 'vitest';

import { renderMarkdown } from './markdown';

describe('renderMarkdown', () => {
  it('renders basic markdown to html', () => {
    const html = renderMarkdown('**bold** and *italic*');
    expect(html).toContain('<strong>bold</strong>');
    expect(html).toContain('<em>italic</em>');
  });

  it('renders fenced code blocks', () => {
    const html = renderMarkdown('```js\nconst a = 1;\n```');
    expect(html).toContain('<pre');
    expect(html).toContain('const');
  });

  it('escapes inline text safely', () => {
    const html = renderMarkdown('plain <script>alert(1)</script> text');
    expect(html).not.toContain('<script>alert(1)</script>');
  });
});
