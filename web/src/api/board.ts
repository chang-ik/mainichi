import { request } from './http';
import type { PostDetail, PostPage } from './types';

export const boardApi = {
  /** 단계별 글 목록 (최신순, 한 페이지 20개) */
  list: (level: number, page = 0) => request<PostPage>(`/board/${level}/posts?page=${page}`),
  write: (level: number, title: string, content: string) =>
    request<PostDetail>(`/board/${level}/posts`, { method: 'POST', body: { title, content } }),
  get: (postId: string) => request<PostDetail>(`/board/posts/${postId}`),
  edit: (postId: string, title: string, content: string) =>
    request<PostDetail>(`/board/posts/${postId}`, { method: 'PATCH', body: { title, content } }),
  remove: (postId: string) => request<void>(`/board/posts/${postId}`, { method: 'DELETE' }),
  /** 댓글을 달고 댓글이 반영된 글 상세를 받는다 */
  comment: (postId: string, content: string) =>
    request<PostDetail>(`/board/posts/${postId}/comments`, { method: 'POST', body: { content } }),
  removeComment: (commentId: string) => request<void>(`/board/comments/${commentId}`, { method: 'DELETE' }),
};

/** 백엔드 PostRequest / CommentRequest 와 같은 한도 */
export const TITLE_MAX = 100;
export const CONTENT_MAX = 5000;
export const COMMENT_MAX = 1000;
