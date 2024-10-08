export interface Review {
  reviewId: string;
  title: string;
  content: string;
  musicalId: string;
}

export interface Category {
  id: string;
  category: string;
}

export interface Musical {
  id: string;
  title: string;
  imageUrl: string;
  category: Category[];
}
