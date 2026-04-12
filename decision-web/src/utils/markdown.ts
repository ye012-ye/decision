import hljs from 'highlight.js/lib/core';
import bash from 'highlight.js/lib/languages/bash';
import javascript from 'highlight.js/lib/languages/javascript';
import json from 'highlight.js/lib/languages/json';
import sql from 'highlight.js/lib/languages/sql';
import typescript from 'highlight.js/lib/languages/typescript';
import xml from 'highlight.js/lib/languages/xml';
import 'highlight.js/styles/github.css';
import { marked } from 'marked';

hljs.registerLanguage('javascript', javascript);
hljs.registerLanguage('typescript', typescript);
hljs.registerLanguage('json', json);
hljs.registerLanguage('bash', bash);
hljs.registerLanguage('shell', bash);
hljs.registerLanguage('sql', sql);
hljs.registerLanguage('xml', xml);
hljs.registerLanguage('html', xml);

marked.use({
  breaks: true,
  gfm: true,
  renderer: {
    code({ text, lang }: { text: string; lang?: string }) {
      const language = lang && hljs.getLanguage(lang) ? lang : 'plaintext';
      const highlighted =
        language === 'plaintext'
          ? escapeHtml(text)
          : hljs.highlight(text, { language }).value;
      return `<pre class="hljs"><code class="language-${language}">${highlighted}</code></pre>`;
    },
    html({ text }: { text: string }) {
      return escapeHtml(text);
    },
  },
});

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function renderMarkdown(source: string): string {
  if (!source) return '';
  return marked.parse(source, { async: false }) as string;
}
